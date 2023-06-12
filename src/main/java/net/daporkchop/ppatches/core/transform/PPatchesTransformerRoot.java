package net.daporkchop.ppatches.core.transform;

import lombok.SneakyThrows;
import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.ClassNode;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;

/**
 * @author DaPorkchop_
 */
public class PPatchesTransformerRoot implements IClassTransformer {
    public static final boolean DUMP_CLASSES = Boolean.getBoolean("ppatches.dumpTransformedClasses");

    public static ITreeClassTransformer[] TRANSFORMERS;

    @SneakyThrows(IOException.class)
    public PPatchesTransformerRoot() {
        if (DUMP_CLASSES && Files.exists(Paths.get(".ppatches_transformed"))) {
            Files.walkFileTree(Paths.get(".ppatches_transformed"), new FileVisitor<Path>() {
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
    }

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        ClassNode classNode = null;
        boolean anyChanged = false;

        for (ITreeClassTransformer transformer : TRANSFORMERS) {
            if (transformer.interestedInClass(name, transformedName)) {
                if (classNode == null) { //this is the first transformer which was interested in transforming the class, it needs to be read into a tree
                    classNode = new ClassNode();
                    ClassReader reader = new ClassReader(basicClass);
                    reader.accept(classNode, 0);
                }

                try {
                    anyChanged |= transformer.transformClass(name, transformedName, classNode);
                } catch (Throwable t) {
                    t.printStackTrace();
                    throw new RuntimeException("failed to transform class " + transformedName, t);
                }
            }
        }

        if (anyChanged) {
            ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
            classNode.accept(writer);

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
}
