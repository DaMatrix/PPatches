package net.daporkchop.ppatches.modules.vanilla.optimizeBufferBuilder;

import com.google.common.base.Preconditions;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import net.daporkchop.ppatches.core.transform.PPatchesTransformerRoot;
import net.daporkchop.ppatches.util.asm.AnonymousClassWriter;
import net.daporkchop.ppatches.util.asm.BytecodeHelper;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.renderer.vertex.VertexFormat;
import net.minecraft.client.renderer.vertex.VertexFormatElement;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

import static org.objectweb.asm.Opcodes.*;

/**
 * @author DaPorkchop_
 */
@RequiredArgsConstructor(access = AccessLevel.PROTECTED)
public abstract class OptimizedVertexFormat {
    @SneakyThrows
    public static OptimizedVertexFormat buildOptimizedVertexFormat(VertexFormat format) {
        Preconditions.checkArgument(!format.getElements().isEmpty(), "vertex format is empty!");

        String internalName = Type.getInternalName(OptimizedVertexFormat.class) + "Impl$" + mangleVertexFormat(format);

        AnonymousClassWriter cw = AnonymousClassWriter.create(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
        cw.visit(V1_8, ACC_PUBLIC | ACC_FINAL, internalName, null, Type.getInternalName(OptimizedVertexFormat.class), null);

        { //constructor
            MethodVisitor mv = cw.visitMethod(ACC_PUBLIC, "<init>", Type.getMethodDescriptor(Type.VOID_TYPE, Type.getType(OptimizedVertexFormatElement.class)), null, null);
            mv.visitCode();

            mv.visitVarInsn(ALOAD, 0);
            mv.visitVarInsn(ALOAD, 1);
            mv.visitMethodInsn(INVOKESPECIAL, Type.getInternalName(OptimizedVertexFormat.class), "<init>", Type.getMethodDescriptor(Type.VOID_TYPE, Type.getType(OptimizedVertexFormatElement.class)), false);
            mv.visitInsn(RETURN);

            mv.visitMaxs(0, 0);
            mv.visitEnd();
        }

        visitVertexSizeGetters(cw, format);

        cw.visitEnd();

        PPatchesTransformerRoot.dumpClass(internalName, cw);

        Class<?> clazz = cw.defineAnonymousClass(OptimizedVertexFormat.class);
        return (OptimizedVertexFormat) MethodHandles.publicLookup()
                .findConstructor(clazz, MethodType.methodType(void.class, OptimizedVertexFormatElement.class))
                .invoke(OptimizedVertexFormatElement.generate(format));
    }

    private final OptimizedVertexFormatElement firstElement;

    public final OptimizedVertexFormatElement getFirstElement() {
        return this.firstElement;
    }

    public abstract int vertexSizeBytes();

    public abstract int vertexSizeInts();

    static String mangleVertexFormat(VertexFormat format) {
        if (format == DefaultVertexFormats.BLOCK) {
            return "BLOCK";
        } else if (format == DefaultVertexFormats.ITEM) {
            return "ITEM";
        } else if (format == DefaultVertexFormats.OLDMODEL_POSITION_TEX_NORMAL) {
            return "OLDMODEL_POSITION_TEX_NORMAL";
        } else if (format == DefaultVertexFormats.PARTICLE_POSITION_TEX_COLOR_LMAP) {
            return "PARTICLE_POSITION_TEX_COLOR_LMAP";
        } else if (format == DefaultVertexFormats.POSITION) {
            return "POSITION";
        } else if (format == DefaultVertexFormats.POSITION_COLOR) {
            return "POSITION_COLOR";
        } else if (format == DefaultVertexFormats.POSITION_TEX) {
            return "POSITION_TEX";
        } else if (format == DefaultVertexFormats.POSITION_NORMAL) {
            return "POSITION_NORMAL";
        } else if (format == DefaultVertexFormats.POSITION_TEX_COLOR) {
            return "POSITION_TEX_COLOR";
        } else if (format == DefaultVertexFormats.POSITION_TEX_NORMAL) {
            return "POSITION_TEX_NORMAL";
        } else if (format == DefaultVertexFormats.POSITION_TEX_LMAP_COLOR) {
            return "POSITION_TEX_LMAP_COLOR";
        } else if (format == DefaultVertexFormats.POSITION_TEX_COLOR_NORMAL) {
            return "POSITION_TEX_COLOR_NORMAL";
        }

        //not a vanilla vertex format, stringify the individual elements
        StringBuilder builder = new StringBuilder();
        boolean first = true;
        for (VertexFormatElement element : format.getElements()) {
            if (!first) {
                builder.append('_');
            }
            first = false;
            builder.append(element.getIndex()).append(element.getUsage()).append('_').append(element.getElementCount()).append('x').append(element.getType());
        }
        return builder.toString();
    }

    static void visitVertexSizeGetters(ClassWriter cw, VertexFormat format) {
        { //int vertexSizeBytes()
            MethodVisitor mv = cw.visitMethod(ACC_PUBLIC | ACC_FINAL, "vertexSizeBytes", Type.getMethodDescriptor(Type.INT_TYPE), null, null);
            mv.visitCode();

            BytecodeHelper.visitLoadConstantInsn(mv, format.getSize());
            mv.visitInsn(IRETURN);

            mv.visitMaxs(0, 0);
            mv.visitEnd();
        }

        { //int vertexSizeInts()
            MethodVisitor mv = cw.visitMethod(ACC_PUBLIC | ACC_FINAL, "vertexSizeInts", Type.getMethodDescriptor(Type.INT_TYPE), null, null);
            mv.visitCode();

            BytecodeHelper.visitLoadConstantInsn(mv, format.getIntegerSize());
            mv.visitInsn(IRETURN);

            mv.visitMaxs(0, 0);
            mv.visitEnd();
        }
    }
}
