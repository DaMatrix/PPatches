package net.daporkchop.ppatches.core.transform;

import lombok.SneakyThrows;
import net.daporkchop.ppatches.PPatchesMod;
import net.daporkchop.ppatches.core.bootstrap.PPatchesBootstrap;
import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.service.ITransformer;
import org.spongepowered.asm.transformers.MixinClassWriter;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @author DaPorkchop_
 */
public final class PPatchesTransformerRoot implements IClassTransformer, ITransformer {
    public static final boolean DUMP_CLASSES = Boolean.getBoolean("ppatches.dumpTransformedClasses");

    private static final List<PPatchesTransformerRoot> INSTANCES = new ArrayList<>();
    private static ITreeClassTransformer[] TRANSFORMERS = new ITreeClassTransformer[0];

    public synchronized static void registerTransformer(ITreeClassTransformer transformer) {
        for (ITreeClassTransformer existingTransformer : TRANSFORMERS) {
            if (transformer == existingTransformer) {
                throw new IllegalArgumentException("transformer " + transformer + " already registered?!?");
            }
        }

        ITreeClassTransformer[] newTransformers = Arrays.copyOf(TRANSFORMERS, TRANSFORMERS.length + 1);
        newTransformers[TRANSFORMERS.length] = transformer;
        Arrays.sort(newTransformers);
        TRANSFORMERS = newTransformers;
    }

    private final PPatchesBootstrap.Phase phase = PPatchesBootstrap.currentPhase();
    private boolean disabled = false;

    @SneakyThrows(IOException.class)
    public PPatchesTransformerRoot() {
        PPatchesMod.LOGGER.info("Registering new transformer at {}", this.phase);

        Path dir;
        if (DUMP_CLASSES && INSTANCES.isEmpty() && Files.exists(dir = Paths.get(".ppatches_transformed"))) {
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

        ClassReader reader = null;
        ClassNode classNode = null;
        int changeFlags = 0;

        for (ITreeClassTransformer transformer : TRANSFORMERS) {
            if (transformer.interestedInClass(name, transformedName)) {
                if (classNode == null) { //this is the first transformer which was interested in transforming the class, it needs to be read into a tree
                    classNode = new ClassNode();
                    reader = new ClassReader(basicClass);
                    reader.accept(classNode, 0);
                }

                try {
                    changeFlags |= transformer.transformClass(name, transformedName, classNode);
                } catch (Throwable t) {
                    t.printStackTrace();
                    throw new RuntimeException("failed to transform class " + transformedName, t);
                }
            }
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

    @Override
    public String getName() {
        return this.getClass().getName();
    }

    @Override
    public boolean isDelegationExcluded() {
        return true;
    }
}
