package net.daporkchop.ppatches.core.transform;

import com.google.common.base.Preconditions;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import net.daporkchop.ppatches.PPatchesMod;
import net.daporkchop.ppatches.core.bootstrap.PPatchesBootstrap;
import net.daporkchop.ppatches.util.COWArrayUtils;
import net.daporkchop.ppatches.util.UnsafeWrapper;
import net.daporkchop.ppatches.util.asm.AnonymousClassWriter;
import net.daporkchop.ppatches.util.asm.InvokeDynamicUtils;
import net.daporkchop.ppatches.util.asm.analysis.AnalyzedInsnList;
import net.daporkchop.ppatches.util.asm.analysis.ReachabilityAnalyzer;
import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.spongepowered.asm.service.ITransformer;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.List;

import static org.objectweb.asm.Opcodes.*;

/**
 * @author DaPorkchop_
 */
public final class PPatchesTransformerRoot implements IClassTransformer, ITransformer {
    private static final boolean DUMP_CLASSES = Boolean.getBoolean("ppatches.dumpTransformedClasses");
    private static final boolean DUMP_CLASSES_DELETE_OLD = DUMP_CLASSES && Boolean.getBoolean("ppatches.dumpTransformedClasses.deleteOld");

    private static final List<PPatchesTransformerRoot> INSTANCES = new ArrayList<>();
    private static TransformerPipeline PIPELINE = buildTransformerPipeline();

    public synchronized static void registerTransformers(ITreeClassTransformer... transformers) {
        if (transformers.length == 0) {
            return;
        }

        for (int i = 0; i < transformers.length; i++) {
            Class<? extends ITreeClassTransformer> transformerClass = transformers[i].getClass();
            for (int j = i + 1; j < transformers.length; j++) {
                Preconditions.checkArgument(transformerClass != transformers[j].getClass(), "transformer %s was registered twice?!?", transformerClass);
            }

            for (ITreeClassTransformer existingTransformer : PIPELINE.allTransformers) {
                Preconditions.checkArgument(transformerClass != existingTransformer.getClass(), "transformer %s was already registered?!?", transformerClass);
            }
        }

        ITreeClassTransformer[] newTransformers = COWArrayUtils.concat(PIPELINE.allTransformers, transformers);
        Arrays.sort(newTransformers);
        PIPELINE = buildTransformerPipeline(newTransformers);
    }

    public static void dumpClass(String name, ClassWriter writer) {
        if (DUMP_CLASSES) {
            try {
                Path path = Paths.get(".ppatches_transformed" + File.separatorChar + name.replace('/', File.separatorChar).replace('.', File.separatorChar) + ".class");
                Files.createDirectories(path.getParent());
                Files.write(path, writer.toByteArray());
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
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
    @SneakyThrows
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (this.disabled || basicClass == null) {
            return basicClass;
        }

        TransformerPipeline pipeline = PIPELINE;

        //determine which transformers are interested in transforming this class
        BitSet interestedMask = pipeline.determineInterested(name, transformedName);

        if (interestedMask.isEmpty()) {
            //no transformers are interested in transforming this class
            return basicClass;
        }

        //at least one transformer is interested in optimizing this class, so we should read it
        ClassReader reader = new ClassReader(basicClass);
        ClassNode classNode = readClass(reader);
        int changeFlags = 0;

        //run regular transformers
        changeFlags |= pipeline.applyRegularTransformers(name, transformedName, classNode, interestedMask);

        //run individual method transformers
        if (pipeline.anyMethodTransformersInterestedInClass(interestedMask)) {
            for (MethodNode methodNode : classNode.methods) { //TODO: doing this in parallel seems impossible, due to LaunchClassLoader not allowing concurrent loading - we'd need to preload all classes which could potentially be accessed by any transformer
                if ((methodNode.access & (Opcodes.ACC_ABSTRACT | Opcodes.ACC_NATIVE)) != 0) { //we can skip transforming abstract and native methods
                    continue;
                }

                //remove unreachable instructions from the list
                ReachabilityAnalyzer.removeUnreachableInstructions(classNode.name, methodNode);

                if (pipeline.anyBasicMethodTransformersInterestedInClass(interestedMask)) {
                    changeFlags |= pipeline.applyBasicMethodTransformers(name, transformedName, classNode, methodNode, interestedMask);

                    //keep running all the optimization passes until they all stop making changes
                    for (int prevRoundChangeFlags; (prevRoundChangeFlags = pipeline.applyBasicMethodOptimizationPasses(name, transformedName, classNode, methodNode, interestedMask)) != 0; ) {
                        changeFlags |= prevRoundChangeFlags;
                    }
                }

                if (pipeline.anyAnalyzedMethodTransformersInterestedInClass(interestedMask)) {
                    try (AnalyzedInsnList analyzedInstructions = new AnalyzedInsnList(classNode.name, methodNode)) {
                        changeFlags |= pipeline.applyAnalyzedMethodTransformers(name, transformedName, classNode, methodNode, analyzedInstructions, interestedMask);

                        //keep running all the optimization passes until they all stop making changes
                        for (int prevRoundChangeFlags; (prevRoundChangeFlags = pipeline.applyAnalyzedMethodOptimizationPasses(name, transformedName, classNode, methodNode, analyzedInstructions, interestedMask)) != 0; ) {
                            changeFlags |= prevRoundChangeFlags;
                        }
                    }
                }
            }
        }

        if ((changeFlags & ITreeClassTransformer.CHANGED) != 0) {
            if (classNode.version < V1_8) {
                PPatchesMod.LOGGER.trace("upgrading {} bytecode version to 1.8 (" + V1_8 + ") from {}", transformedName, classNode.version);
                classNode.version = V1_8;
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

            dumpClass(transformedName, writer);

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
                case 7: { //CONSTANT_Class
                    String desc = reader.readUTF8(index, buf);
                    if (desc.charAt(0) == '[') {
                        int classNameStart = desc.indexOf('L');
                        if (classNameStart >= 0) { //desc represents an array of objects
                            result.add(desc.substring(classNameStart + 1, desc.length() - 1));
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
               && !transformedName.startsWith(transformer.getClass().getName()); //prevent transformers from transforming their own inner classes
    }

    private static int bitsetArrayLength(int length) {
        return ((length - 1) >> 6) + 1;
    }

    @SneakyThrows
    private static TransformerPipeline buildTransformerPipeline(ITreeClassTransformer... transformers) {
        String className = Type.getInternalName(TransformerPipeline.class) + "$OptimizedPipeline$" + transformers.length;

        AnonymousClassWriter cw = AnonymousClassWriter.create(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);

        cw.visit(V1_8, ACC_PUBLIC | ACC_FINAL, className, null, Type.getInternalName(TransformerPipeline.class), null);

        IntArrayList regularTransformers = new IntArrayList();
        IntArrayList allMethodTransformers = new IntArrayList();
        IntArrayList allBasicMethodTransformers = new IntArrayList();
        IntArrayList basicMethodTransformers = new IntArrayList();
        IntArrayList basicMethodOptimizationPasses = new IntArrayList();
        IntArrayList allAnalyzedMethodTransformers = new IntArrayList();
        IntArrayList analyzedMethodTransformers = new IntArrayList();
        IntArrayList analyzedMethodOptimizationPasses = new IntArrayList();
        for (int i = 0; i < transformers.length; i++) {
            ITreeClassTransformer transformer = transformers[i];
            boolean optimization = transformer instanceof ITreeClassTransformer.OptimizationPass;
            if (transformer instanceof ITreeClassTransformer.IndividualMethod) {
                allMethodTransformers.add(i);
                if (transformer instanceof ITreeClassTransformer.IndividualMethod.Analyzed) {
                    allBasicMethodTransformers.add(i);
                    (optimization ? analyzedMethodOptimizationPasses : analyzedMethodTransformers).add(i);
                } else {
                    allAnalyzedMethodTransformers.add(i);
                    (optimization ? basicMethodOptimizationPasses : basicMethodTransformers).add(i);
                }
            } else {
                Preconditions.checkArgument(!optimization, "non-method optimization passes aren't supported!", transformer);
                regularTransformers.add(i);
            }
        }

        { //constructor
            MethodVisitor mv = cw.visitMethod(ACC_PUBLIC, "<init>", Type.getMethodDescriptor(Type.VOID_TYPE, Type.getType(ITreeClassTransformer[].class)), null, null);
            mv.visitCode();

            mv.visitVarInsn(ALOAD, 0);
            mv.visitVarInsn(ALOAD, 1);
            mv.visitMethodInsn(INVOKESPECIAL, Type.getInternalName(TransformerPipeline.class), "<init>", Type.getMethodDescriptor(Type.VOID_TYPE, Type.getType(ITreeClassTransformer[].class)), false);
            mv.visitInsn(RETURN);

            mv.visitMaxs(0, 0);
            mv.visitEnd();
        }

        { //BitSet determineInterested(String name, String internalName)
            MethodVisitor mv = cw.visitMethod(ACC_PUBLIC, "determineInterested", Type.getMethodDescriptor(Type.getType(BitSet.class), Type.getType(String.class), Type.getType(String.class)), null, null);
            mv.visitCode();

            mv.visitTypeInsn(NEW, Type.getInternalName(BitSet.class));
            mv.visitInsn(DUP);
            mv.visitLdcInsn(transformers.length);
            mv.visitMethodInsn(INVOKESPECIAL, Type.getInternalName(BitSet.class), "<init>", "(I)V", false);
            mv.visitVarInsn(ASTORE, 3);

            for (int i = 0; i < transformers.length; i++) {
                ITreeClassTransformer transformer = transformers[i];

                Label falseLbl = new Label();
                Label tailLbl = new Label();

                mv.visitVarInsn(ALOAD, 3);
                mv.visitLdcInsn(i);
                mv.visitVarInsn(ALOAD, 2);
                mv.visitLdcInsn(transformer.getClass().getName());
                mv.visitMethodInsn(INVOKEVIRTUAL, Type.getInternalName(String.class), "startsWith", Type.getMethodDescriptor(Type.BOOLEAN_TYPE, Type.getType(String.class)), false);
                mv.visitJumpInsn(IFNE, falseLbl);
                cw.addConstant(mv, transformer, Type.getInternalName(ITreeClassTransformer.class));
                mv.visitVarInsn(ALOAD, 1);
                mv.visitVarInsn(ALOAD, 2);
                mv.visitMethodInsn(INVOKEINTERFACE, Type.getInternalName(ITreeClassTransformer.class), "interestedInClass", "(Ljava/lang/String;Ljava/lang/String;)Z", true);
                mv.visitJumpInsn(IFEQ, falseLbl);
                mv.visitInsn(ICONST_1);
                mv.visitJumpInsn(GOTO, tailLbl);
                mv.visitLabel(falseLbl);
                mv.visitInsn(ICONST_0);
                mv.visitLabel(tailLbl);
                mv.visitMethodInsn(INVOKEVIRTUAL, Type.getInternalName(BitSet.class), "set", "(IZ)V", false);
            }

            mv.visitVarInsn(ALOAD, 3);
            mv.visitInsn(ARETURN);

            mv.visitMaxs(0, 0);
            mv.visitEnd();
        }

        { //int applyRegularTransformers(String name, String internalName, ClassNode classNode, BitSet interestedMask)
            MethodVisitor mv = cw.visitMethod(ACC_PUBLIC, "applyRegularTransformers", Type.getMethodDescriptor(Type.INT_TYPE, Type.getType(String.class), Type.getType(String.class), Type.getType(ClassNode.class), Type.getType(BitSet.class)), null, null);
            mv.visitCode();

            mv.visitInsn(ICONST_0);
            mv.visitVarInsn(ISTORE, 5);

            for (int i : regularTransformers) {
                Label tailLbl = new Label();

                mv.visitVarInsn(ALOAD, 4);
                mv.visitLdcInsn(i);
                mv.visitMethodInsn(INVOKEVIRTUAL, Type.getInternalName(BitSet.class), "get", "(I)Z", false);
                mv.visitJumpInsn(IFEQ, tailLbl);

                mv.visitVarInsn(ILOAD, 5);
                cw.addConstant(mv, transformers[i], Type.getInternalName(ITreeClassTransformer.class));
                mv.visitVarInsn(ALOAD, 1);
                mv.visitVarInsn(ALOAD, 2);
                mv.visitVarInsn(ALOAD, 3);
                mv.visitMethodInsn(INVOKEINTERFACE, Type.getInternalName(ITreeClassTransformer.class), "transformClass", Type.getMethodDescriptor(Type.INT_TYPE, Type.getType(String.class), Type.getType(String.class), Type.getType(ClassNode.class)), true);
                mv.visitInsn(IOR);
                mv.visitVarInsn(ISTORE, 5);

                mv.visitLabel(tailLbl);
            }

            mv.visitVarInsn(ILOAD, 5);
            mv.visitInsn(IRETURN);

            mv.visitMaxs(0, 0);
            mv.visitEnd();
        }

        visitAnyInterestedMethodInPipeline(cw, transformers, "anyMethodTransformersInterestedInClass", allMethodTransformers);

        visitAnyInterestedMethodInPipeline(cw, transformers, "anyBasicMethodTransformersInterestedInClass", allBasicMethodTransformers);
        visitApplyMethodTransformersMethodInPipeline(cw, transformers, "applyBasicMethodTransformers", basicMethodTransformers, false);
        visitApplyMethodTransformersMethodInPipeline(cw, transformers, "applyBasicMethodOptimizationPasses", basicMethodOptimizationPasses, false);

        visitAnyInterestedMethodInPipeline(cw, transformers, "anyAnalyzedMethodTransformersInterestedInClass", allAnalyzedMethodTransformers);
        visitApplyMethodTransformersMethodInPipeline(cw, transformers, "applyAnalyzedMethodTransformers", analyzedMethodTransformers, true);
        visitApplyMethodTransformersMethodInPipeline(cw, transformers, "applyAnalyzedMethodOptimizationPasses", analyzedMethodOptimizationPasses, true);

        cw.visitEnd();

        dumpClass(className, cw);

        return (TransformerPipeline) MethodHandles.publicLookup().findConstructor(cw.defineAnonymousClass(PPatchesTransformerRoot.class), MethodType.methodType(void.class, ITreeClassTransformer[].class)).invoke(transformers);
    }
    
    private static void visitAnyInterestedMethodInPipeline(AnonymousClassWriter cw, ITreeClassTransformer[] transformers, String methodName, IntArrayList transformerIndices) {
        MethodVisitor mv = cw.visitMethod(ACC_PUBLIC, methodName, Type.getMethodDescriptor(Type.BOOLEAN_TYPE, Type.getType(BitSet.class)), null, null);
        mv.visitCode();

        Label trueLbl = new Label();
        Label tailLbl = new Label();

        for (int i : transformerIndices) {
            mv.visitVarInsn(ALOAD, 1);
            mv.visitLdcInsn(i);
            mv.visitMethodInsn(INVOKEVIRTUAL, Type.getInternalName(BitSet.class), "get", "(I)Z", false);
            mv.visitJumpInsn(IFNE, trueLbl);
        }

        mv.visitInsn(ICONST_0);
        mv.visitJumpInsn(GOTO, tailLbl);
        mv.visitLabel(trueLbl);
        mv.visitInsn(ICONST_1);
        mv.visitLabel(tailLbl);
        mv.visitInsn(IRETURN);

        mv.visitMaxs(0, 0);
        mv.visitEnd();
    }

    private static void visitApplyMethodTransformersMethodInPipeline(AnonymousClassWriter cw, ITreeClassTransformer[] transformers, String methodName, IntArrayList transformerIndices, boolean analyzed) {
        String methodDesc;
        String transformMethodDesc;
        String transformerClassInternalName;
        int interestedFlagsLvtIndex;
        int accumulatorLvtIndex;
        if (analyzed) {
            methodDesc = Type.getMethodDescriptor(Type.INT_TYPE, Type.getType(String.class), Type.getType(String.class), Type.getType(ClassNode.class), Type.getType(MethodNode.class), Type.getType(AnalyzedInsnList.class), Type.getType(BitSet.class));
            transformMethodDesc = Type.getMethodDescriptor(Type.INT_TYPE, Type.getType(String.class), Type.getType(String.class), Type.getType(ClassNode.class), Type.getType(MethodNode.class), Type.getType(AnalyzedInsnList.class));
            transformerClassInternalName = Type.getInternalName(ITreeClassTransformer.IndividualMethod.Analyzed.class);
            interestedFlagsLvtIndex = 6;
            accumulatorLvtIndex = 7;
        } else {
            methodDesc = Type.getMethodDescriptor(Type.INT_TYPE, Type.getType(String.class), Type.getType(String.class), Type.getType(ClassNode.class), Type.getType(MethodNode.class), Type.getType(BitSet.class));
            transformMethodDesc = Type.getMethodDescriptor(Type.INT_TYPE, Type.getType(String.class), Type.getType(String.class), Type.getType(ClassNode.class), Type.getType(MethodNode.class));
            transformerClassInternalName = Type.getInternalName(ITreeClassTransformer.IndividualMethod.class);
            interestedFlagsLvtIndex = 5;
            accumulatorLvtIndex = 6;
        }

        MethodVisitor mv = cw.visitMethod(ACC_PUBLIC, methodName, methodDesc, null, null);
        mv.visitCode();

        mv.visitInsn(ICONST_0);
        mv.visitVarInsn(ISTORE, accumulatorLvtIndex);

        for (int i : transformerIndices) {
            Label tailLbl = new Label();

            mv.visitVarInsn(ALOAD, interestedFlagsLvtIndex);
            mv.visitLdcInsn(i);
            mv.visitMethodInsn(INVOKEVIRTUAL, Type.getInternalName(BitSet.class), "get", "(I)Z", false);
            mv.visitJumpInsn(IFEQ, tailLbl);

            mv.visitVarInsn(ILOAD, accumulatorLvtIndex);
            cw.addConstant(mv, transformers[i], transformerClassInternalName);
            mv.visitVarInsn(ALOAD, 1);
            mv.visitVarInsn(ALOAD, 2);
            mv.visitVarInsn(ALOAD, 3);
            mv.visitVarInsn(ALOAD, 4);
            if (analyzed) {
                mv.visitVarInsn(ALOAD, 5);
            }
            mv.visitMethodInsn(INVOKEINTERFACE, transformerClassInternalName, "transformMethod", transformMethodDesc, true);
            mv.visitInsn(IOR);
            mv.visitVarInsn(ISTORE, accumulatorLvtIndex);

            mv.visitLabel(tailLbl);
        }

        mv.visitVarInsn(ILOAD, accumulatorLvtIndex);
        mv.visitInsn(IRETURN);

        mv.visitMaxs(0, 0);
        mv.visitEnd();
    }

    private static ClassNode readClass(ClassReader reader) {
        ClassNode classNode = new ClassNode();

        //we use SKIP_FRAMES to help make transformers run slightly faster, as there will be slightly fewer instructions which need to be skipped over
        reader.accept(classNode, ClassReader.SKIP_FRAMES);

        return classNode;
    }

    @RequiredArgsConstructor
    private static abstract class TransformerPipeline {
        public final ITreeClassTransformer[] allTransformers;

        public abstract BitSet determineInterested(String name, String transformedName);

        public abstract int applyRegularTransformers(String name, String transformedName, ClassNode classNode, BitSet interestedMask);

        public abstract boolean anyMethodTransformersInterestedInClass(BitSet interestedMask);

        public abstract boolean anyBasicMethodTransformersInterestedInClass(BitSet interestedMask);

        public abstract int applyBasicMethodTransformers(String name, String transformedName, ClassNode classNode, MethodNode methodNode, BitSet interestedMask);

        public abstract int applyBasicMethodOptimizationPasses(String name, String transformedName, ClassNode classNode, MethodNode methodNode, BitSet interestedMask);

        public abstract boolean anyAnalyzedMethodTransformersInterestedInClass(BitSet interestedMask);

        public abstract int applyAnalyzedMethodTransformers(String name, String transformedName, ClassNode classNode, MethodNode methodNode, AnalyzedInsnList instructions, BitSet interestedMask);

        public abstract int applyAnalyzedMethodOptimizationPasses(String name, String transformedName, ClassNode classNode, MethodNode methodNode, AnalyzedInsnList instructions, BitSet interestedMask);
    }
}
