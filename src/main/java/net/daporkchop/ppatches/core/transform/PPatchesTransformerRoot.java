package net.daporkchop.ppatches.core.transform;

import com.google.common.base.Preconditions;
import lombok.SneakyThrows;
import net.daporkchop.ppatches.PPatchesMod;
import net.daporkchop.ppatches.core.bootstrap.PPatchesBootstrap;
import net.daporkchop.ppatches.util.asm.analysis.AnalyzedInsnList;
import net.minecraft.launchwrapper.IClassTransformer;
import net.minecraft.launchwrapper.Launch;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FrameNode;
import org.objectweb.asm.tree.MethodNode;
import org.spongepowered.asm.service.ITransformer;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;

/**
 * @author DaPorkchop_
 */
public final class PPatchesTransformerRoot implements IClassTransformer, ITransformer {
    private static final boolean DUMP_CLASSES = Boolean.getBoolean("ppatches.dumpTransformedClasses");
    private static final boolean DUMP_CLASSES_DELETE_OLD = DUMP_CLASSES && Boolean.getBoolean("ppatches.dumpTransformedClasses.deleteOld");

    private static final List<PPatchesTransformerRoot> INSTANCES = new ArrayList<>();
    private static TransformerPipeline PIPELINE = new TransformerPipeline();

    public synchronized static void registerTransformer(ITreeClassTransformer transformer) {
        for (ITreeClassTransformer existingTransformer : PIPELINE.allTransformers) {
            if (transformer == existingTransformer) {
                throw new IllegalArgumentException("transformer " + transformer + " already registered?!?");
            }
        }

        //preloadReferencedClasses(transformer.getClass().getName());

        ITreeClassTransformer[] newTransformers = Arrays.copyOf(PIPELINE.allTransformers, PIPELINE.allTransformers.length + 1);
        newTransformers[PIPELINE.allTransformers.length] = transformer;
        Arrays.sort(newTransformers);
        PIPELINE = new TransformerPipeline(newTransformers);
    }

    private final PPatchesBootstrap.Phase phase = PPatchesBootstrap.currentPhase();
    private boolean disabled = false;

    @SneakyThrows(IOException.class)
    public PPatchesTransformerRoot() {
        PPatchesMod.LOGGER.info("Registering new transformer at {}", this.phase);

        Path dir;
        if (DUMP_CLASSES_DELETE_OLD && INSTANCES.isEmpty() && Files.exists(dir = Paths.get(".ppatches_transformed"))) {
            Files.walkFileTree(dir, new FileVisitor<Path>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Files.delete(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
                    throw exc;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                    if (exc == null) {
                        Files.delete(dir);
                        return FileVisitResult.CONTINUE;
                    } else {
                        throw exc;
                    }
                }
            });
        }

        for (PPatchesTransformerRoot oldInstance : INSTANCES) {
            if (oldInstance.phase.ordinal() >= this.phase.ordinal()) {
                throw new IllegalStateException("a transformer already exists at phase " + oldInstance.phase + ", so we can't start at " + this.phase);
            }
            oldInstance.disabled = true;
        }

        INSTANCES.add(this);
    }

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (this.disabled || basicClass == null) {
            return basicClass;
        }

        TransformerPipeline pipeline = PIPELINE;

        //determine which transformers are interested in transforming this class
        BitSet interestedRegularTransformers = new BitSet(pipeline.regularTransformers.length);
        BitSet interestedMethodTransformers = new BitSet(pipeline.methodTransformers.length);
        BitSet interestedMethodOptimizationPasses = new BitSet(pipeline.methodOptimizationPasses.length);
        for (int i = 0; i < pipeline.regularTransformers.length; i++) {
            if (transformerInterestedInClass(pipeline.regularTransformers[i], name, transformedName)) {
                interestedRegularTransformers.set(i);
            }
        }
        for (int i = 0; i < pipeline.methodTransformers.length; i++) {
            if (transformerInterestedInClass(pipeline.methodTransformers[i], name, transformedName)) {
                interestedMethodTransformers.set(i);
            }
        }
        for (int i = 0; i < pipeline.methodOptimizationPasses.length; i++) {
            if (transformerInterestedInClass(pipeline.methodOptimizationPasses[i], name, transformedName)) {
                interestedMethodOptimizationPasses.set(i);
            }
        }

        if (interestedRegularTransformers.isEmpty() && interestedMethodTransformers.isEmpty() && interestedMethodOptimizationPasses.isEmpty()) {
            //no transformers are interested in transforming this class
            return basicClass;
        }

        //at least one transformer is interested in optimizing this class, so we should read it
        ClassReader reader = new ClassReader(basicClass);
        ClassNode classNode = readClass(reader);
        int changeFlags = 0;

        //run regular transformers
        for (int i = interestedRegularTransformers.nextSetBit(0); i >= 0; i = interestedRegularTransformers.nextSetBit(i + 1)) {
            changeFlags |= pipeline.regularTransformers[i].transformClass(name, transformedName, classNode);
        }

        //run individual method transformers
        if (!interestedMethodTransformers.isEmpty() || !interestedMethodOptimizationPasses.isEmpty()) {
            changeFlags |= classNode.methods.stream() //TODO: doing this in parallel seems impossible, due to LaunchClassLoader not allowing concurrent loading
                    .mapToInt(methodNode -> {
                        int methodChangeFlags = 0;

                        /*if (interestedMethodTransformers.isEmpty() && !methodNode.tryCatchBlocks.isEmpty()) { //TODO: this is a gross hack
                            return methodChangeFlags;
                        }*/

                        try (AnalyzedInsnList analyzedInstructions = new AnalyzedInsnList(classNode.name, methodNode)) {
                            for (int i = interestedMethodTransformers.nextSetBit(0); i >= 0; i = interestedMethodTransformers.nextSetBit(i + 1)) {
                                methodChangeFlags |= pipeline.methodTransformers[i].transformMethod(name, transformedName, classNode, methodNode, analyzedInstructions);
                            }

                            //keep running all the optimization passes until they all stop making changes
                            int prevRoundChangeFlags;
                            do {
                                prevRoundChangeFlags = 0;

                                for (int i = interestedMethodOptimizationPasses.nextSetBit(0); i >= 0; i = interestedMethodOptimizationPasses.nextSetBit(i + 1)) {
                                    prevRoundChangeFlags |= pipeline.methodOptimizationPasses[i].transformMethod(name, transformedName, classNode, methodNode, analyzedInstructions);
                                }

                                methodChangeFlags |= prevRoundChangeFlags;
                            } while (prevRoundChangeFlags != 0);
                        }

                        return methodChangeFlags;
                    })
                    .reduce(0, (a, b) -> a | b);
        }

        if ((changeFlags & ITreeClassTransformer.CHANGED) != 0) {
            if (classNode.version < Opcodes.V1_8) {
                PPatchesMod.LOGGER.trace("upgrading {} bytecode version to 1.8 (" + Opcodes.V1_8 + ") from {}", transformedName, classNode.version);
                classNode.version = Opcodes.V1_8;
            }

            ClassWriter writer = new PPatchesClassWriter(reader, ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
            try {
                classNode.accept(writer);
            } catch (PPatchesClassWriter.UnknownCommonSuperClassException e) {
                //this can only occur in a few very rare situations where two different object types are being merged together from different sides of a branch, and one of the types refers
                //  to a class which isn't available at runtime. this makes automatic computation of the resulting stackmap frame effectively impossible (technically it would still be
                //  possible, as if we assume that none of our transformers are able to cause something like that to occur, we could "simply" derive the expected common superclass from the
                //  existing stackmap frames in the original bytecode, but extremely tedious and not worthwhile for a situation which seems to be exceedingly rare), so rather than go through
                //  great pain to implement this correctly i've elected to simply allow the class to be ignored entirely.
                //of course, if a transformer made changes to this class which it considers mandatory (e.g. it adds a new method which must be accessible from a different class), we must crash
                //  the game!
                if ((changeFlags & ITreeClassTransformer.CHANGED_MANDATORY) == ITreeClassTransformer.CHANGED_MANDATORY) { //some mandatory changes were made to this class which we aren't allowed to omit, crash the game
                    throw e;
                } else {
                    PPatchesMod.LOGGER.fatal("Unable to patch class " + classNode.name + ", it will not be modified!", e);
                    return basicClass;
                }
            }

            if (DUMP_CLASSES) {
                try {
                    Path path = Paths.get(".ppatches_transformed" + File.separatorChar + transformedName.replace('.', File.separatorChar) + ".class");
                    Files.createDirectories(path.getParent());
                    Files.write(path, writer.toByteArray());
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }

            return writer.toByteArray();
        } else {
            return basicClass;
        }
    }

    /*@SneakyThrows({ClassNotFoundException.class, IOException.class})
    private static void preloadReferencedClasses(String className) {
        Queue<String> queue = new ArrayDeque<>();
        queue.add(className);

        Set<String> loadedClasses = new HashSet<>();
        for (String name; (name = queue.poll()) != null; ) {
            if (!name.startsWith("java.") && loadedClasses.add(name)) {
                Launch.classLoader.findClass(name);

                for (String referencedName : referencedClassNames(new ClassReader(Launch.classLoader.getClassBytes(name)))) {
                    queue.add(referencedName.replace('/', '.'));
                }
            }
        }
    }

    private static List<String> referencedClassNames(ClassReader reader) {
        char[] buf = new char[reader.getMaxStringLength()];

        List<String> result = new ArrayList<>();
        for (int item = 0; item < reader.getItemCount(); item++) { //iterate over all the items in the constant pool
            int index = reader.getItem(item);
            if (index == 0) { //empty item (seems to happen sporadically)
                continue;
            }
            switch (reader.readByte(index - 1)) {
                case 9: { //CONSTANT_Class
                    String desc = reader.readClass(index, buf);
                    if (desc.charAt(0) == '[') {
                        int classNameStart = desc.indexOf('L');
                        if (classNameStart >= 0) { //desc represents an array of objects
                            result.add(desc.substring(classNameStart + 1));
                        }
                    } else {
                        result.add(desc);
                    }
                    break;
                }
                case 16: { //CONSTANT_MethodType
                    String desc = reader.readUTF8(index, buf);
                    //this will only pick up the base object types and skip over everything else (arrays, primitives, parentheses)
                    for (int startIndex = desc.indexOf('L'); startIndex >= 0; startIndex = desc.indexOf('L', startIndex + 1)) {
                        int endIndex = desc.indexOf(';', startIndex + 1);
                        result.add(desc.substring(startIndex + 1, endIndex));
                        startIndex = endIndex;
                    }
                    break;
                }
            }
        }
        return result;
    }*/

    @Override
    public String getName() {
        return this.getClass().getName();
    }

    @Override
    public boolean isDelegationExcluded() {
        return true;
    }

    private static boolean transformerInterestedInClass(ITreeClassTransformer transformer, String name, String transformedName) {
        return transformer.interestedInClass(name, transformedName)
               && !transformedName.startsWith(transformer.getClass().getTypeName()); //prevent transformers from transforming their own inner classes
    }

    private static ClassNode readClass(ClassReader reader) {
        ClassNode classNode = new ClassNode();

        //we use SKIP_FRAMES to help make transformers run slightly faster, as there will be slightly fewer instructions which need to be skipped over
        reader.accept(classNode, ClassReader.SKIP_FRAMES);

        return classNode;
    }

    private static final class TransformerPipeline {
        public final ITreeClassTransformer[] allTransformers;

        public final ITreeClassTransformer[] regularTransformers;
        public final ITreeClassTransformer.IndividualMethod[] methodTransformers;
        public final ITreeClassTransformer.IndividualMethod[] methodOptimizationPasses;

        public TransformerPipeline(ITreeClassTransformer... allTransformers) {
            this.allTransformers = allTransformers;

            List<ITreeClassTransformer> regularTransformers = new ArrayList<>(allTransformers.length);
            List<ITreeClassTransformer.IndividualMethod> methodTransformers = new ArrayList<>(allTransformers.length);
            List<ITreeClassTransformer.IndividualMethod> methodOptimizationPasses = new ArrayList<>(allTransformers.length);
            for (ITreeClassTransformer transformer : allTransformers) {
                boolean optimization = transformer instanceof ITreeClassTransformer.OptimizationPass;
                if (transformer instanceof ITreeClassTransformer.IndividualMethod) {
                    (optimization ? methodOptimizationPasses : methodTransformers).add((ITreeClassTransformer.IndividualMethod) transformer);
                } else {
                    Preconditions.checkArgument(!optimization, "non-method optimization passes aren't supported!", transformer);
                    regularTransformers.add(transformer);
                }
            }

            this.regularTransformers = regularTransformers.toArray(new ITreeClassTransformer[0]);
            this.methodTransformers = methodTransformers.toArray(new ITreeClassTransformer.IndividualMethod[0]);
            this.methodOptimizationPasses = methodOptimizationPasses.toArray(new ITreeClassTransformer.IndividualMethod[0]);
        }
    }
}
