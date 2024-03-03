package net.daporkchop.ppatches.modules.vanilla.optimizeBufferBuilder;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import net.daporkchop.ppatches.PPatchesConfig;
import net.daporkchop.ppatches.core.transform.PPatchesTransformerRoot;
import net.daporkchop.ppatches.util.COWArrayUtils;
import net.daporkchop.ppatches.util.UnsafeWrapper;
import net.daporkchop.ppatches.util.asm.AnonymousClassWriter;
import net.daporkchop.ppatches.util.asm.BytecodeHelper;
import net.minecraft.client.renderer.vertex.VertexFormat;
import net.minecraft.client.renderer.vertex.VertexFormatElement;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Handle;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;

import java.lang.invoke.CallSite;
import java.lang.invoke.ConstantCallSite;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Objects;

import static org.objectweb.asm.Opcodes.*;

/**
 * @author DaPorkchop_
 */
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public abstract class OptimizedVertexFormatElement {
    /*
     * There are two different (but similar) implementations of code generation:
     * - 'ChainedInstances' generates one class for each vertex attribute, where each one implements all attribute setter methods. each
     *   instance has a **CONSTANT** reference to the next non-padding element which is returned by each of the attribute setters. the
     *   returned next element is stored in the BufferBuilder after writing each attribute (which avoids a slow modulo calculation after
     *   every single attribute is written, which may also recurse; replacing it with a simple constant load followed by storing it in
     *   the corresponding BufferBuilder field.
     *   unfortunately, HotSpot C2 seems to be unable to follow constants which are stored in a field and then loaded again, so while
     *   this is already quite a bit faster than the vanilla approach it still isn't quite optimal. (aside: the Zing/Azul Prime JVM does
     *   appear to be able to optimize this)
     * - 'AssumeValidVertexFormat' works around this by assuming all vertex attributes are unique and accessed in the correct order,
     *   and then generates a single class where each attribute setter method is implemented specifically for the corresponding vertex
     *   attribute. this would result in inconsistent behavior compared to vanilla if someone uses the BufferBuilder incorrectly, but
     *   since it eliminates the need to keep replacing the OptimizedVertexFormatElement instance stored in the BufferBuilder HotSpot
     *   C2 is able to see that consecutive attribute setters all go to the same class and can speculatively inline much better.
     */

    //store this in a global constant so that it's consistent across all classes and so that it can be constant folded
    public static final boolean ASSUME_VALID_VERTEX_FORMAT = PPatchesConfig.vanilla_optimizeBufferBuilder.assumeValidVertexFormat;

    public abstract int vertexSizeBytes();

    public abstract int vertexSizeInts();

    public abstract OptimizedVertexFormatElement next();

    public abstract OptimizedVertexFormatElement tex(ByteBuffer buffer, int vertexCount, double u, double v);

    public abstract OptimizedVertexFormatElement lightmap(ByteBuffer buffer, int vertexCount, int skyLight, int blockLight);

    public abstract OptimizedVertexFormatElement color(ByteBuffer buffer, int vertexCount, int r, int g, int b, int a);

    public abstract OptimizedVertexFormatElement pos(ByteBuffer buffer, int vertexCount, double x, double y, double z);

    public abstract OptimizedVertexFormatElement normal(ByteBuffer buffer, int vertexCount, float x, float y, float z);

    //
    // TYPE-SPECIFIC ATTRIBUTE SETTER IMPLEMENTATIONS
    //   these contain equivalent code to the corresponding case statement in the switch in the corresponding method in BufferBuilder. they're
    //   written out by hand because there's so many inconsistencies with how the values are handled that doing codegen on them directly would
    //   be far more hassle.
    //   the 'offset' parameter corresponds to 'int i = this.vertexCount * this.vertexFormat.getSize() + this.vertexFormat.getOffset(this.vertexFormatIndex);'
    //   in the vanilla attribute setter methods.
    //

    protected static void texFLOAT(long address, double u, double v) {
        UnsafeWrapper.putFloat(address, (float) u);
        UnsafeWrapper.putFloat(address + 4, (float) v);
    }
    protected static void texINT(long address, double u, double v) {
        UnsafeWrapper.putInt(address, (int) u);
        UnsafeWrapper.putInt(address + 4, (int) v);
    }
    protected static void texSHORT(long address, double u, double v) {
        UnsafeWrapper.putShort(address, (short) u);
        UnsafeWrapper.putShort(address + 2, (short) v);
    }
    protected static void texBYTE(long address, double u, double v) {
        UnsafeWrapper.putByte(address, (byte) u);
        UnsafeWrapper.putByte(address + 1, (byte) v);
    }

    protected static void lightmapFLOAT(long address, int skyLight, int blockLight) {
        UnsafeWrapper.putFloat(address, (float) skyLight);
        UnsafeWrapper.putFloat(address + 4, (float) blockLight);
    }
    protected static void lightmapINT(long address, int skyLight, int blockLight) {
        UnsafeWrapper.putInt(address, skyLight);
        UnsafeWrapper.putInt(address + 4, blockLight);
    }
    protected static void lightmapSHORT(long address, int skyLight, int blockLight) {
        UnsafeWrapper.putShort(address, (short) skyLight);
        UnsafeWrapper.putShort(address + 2, (short) blockLight);
    }
    protected static void lightmapBYTE(long address, int skyLight, int blockLight) {
        UnsafeWrapper.putByte(address, (byte) skyLight);
        UnsafeWrapper.putByte(address + 1, (byte) blockLight);
    }

    protected static void colorFLOAT(long address, int r, int g, int b, int a) {
        UnsafeWrapper.putFloat(address, (float) r / 255.0f);
        UnsafeWrapper.putFloat(address + 4, (float) g / 255.0f);
        UnsafeWrapper.putFloat(address + 8, (float) b / 255.0f);
        UnsafeWrapper.putFloat(address + 12, (float) a / 255.0f);
    }
    protected static void colorINT(long address, int r, int g, int b, int a) {
        //not a typo, the vanilla code is actually just this dumb
        UnsafeWrapper.putFloat(address, (float) r);
        UnsafeWrapper.putFloat(address + 4, (float) g);
        UnsafeWrapper.putFloat(address + 8, (float) b);
        UnsafeWrapper.putFloat(address + 12, (float) a);
    }
    protected static void colorSHORT(long address, int r, int g, int b, int a) {
        UnsafeWrapper.putShort(address, (short) r);
        UnsafeWrapper.putShort(address + 2, (short) g);
        UnsafeWrapper.putShort(address + 4, (short) b);
        UnsafeWrapper.putShort(address + 6, (short) a);
    }
    protected static void colorBYTE(long address, int r, int g, int b, int a) {
        //this check should be able to be constant folded
        if (ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN) {
            UnsafeWrapper.putByte(address, (byte) r);
            UnsafeWrapper.putByte(address + 1, (byte) g);
            UnsafeWrapper.putByte(address + 2, (byte) b);
            UnsafeWrapper.putByte(address + 3, (byte) a);
        } else {
            UnsafeWrapper.putByte(address, (byte) a);
            UnsafeWrapper.putByte(address + 1, (byte) b);
            UnsafeWrapper.putByte(address + 2, (byte) g);
            UnsafeWrapper.putByte(address + 3, (byte) r);
        }
    }

    protected static void posFLOAT(long address, double x, double y, double z) {
        UnsafeWrapper.putFloat(address, (float) x);
        UnsafeWrapper.putFloat(address + 4, (float) y);
        UnsafeWrapper.putFloat(address + 8, (float) z);
    }
    protected static void posINT(long address, double x, double y, double z) {
        UnsafeWrapper.putInt(address, Float.floatToRawIntBits((float) x));
        UnsafeWrapper.putInt(address + 4, Float.floatToRawIntBits((float) y));
        UnsafeWrapper.putInt(address + 8, Float.floatToRawIntBits((float) z));
    }
    protected static void posSHORT(long address, double x, double y, double z) {
        UnsafeWrapper.putShort(address, (short) x);
        UnsafeWrapper.putShort(address + 2, (short) y);
        UnsafeWrapper.putShort(address + 4, (short) z);
    }
    protected static void posBYTE(long address, double x, double y, double z) {
        UnsafeWrapper.putByte(address, (byte) x);
        UnsafeWrapper.putByte(address + 1, (byte) y);
        UnsafeWrapper.putByte(address + 2, (byte) z);
    }

    protected static void normalFLOAT(long address, float x, float y, float z) {
        UnsafeWrapper.putFloat(address, x);
        UnsafeWrapper.putFloat(address + 4, y);
        UnsafeWrapper.putFloat(address + 8, z);
    }
    protected static void normalINT(long address, float x, float y, float z) {
        UnsafeWrapper.putInt(address, (int) x);
        UnsafeWrapper.putInt(address + 4, (int) y);
        UnsafeWrapper.putInt(address + 8, (int) z);
    }
    protected static void normalSHORT(long address, float x, float y, float z) {
        UnsafeWrapper.putShort(address, (short) (x * 32767.0f));
        UnsafeWrapper.putShort(address + 2, (short) (y * 32767.0f));
        UnsafeWrapper.putShort(address + 4, (short) (z * 32767.0f));
    }
    protected static void normalBYTE(long address, float x, float y, float z) {
        UnsafeWrapper.putByte(address, (byte) (x * 127.0f));
        UnsafeWrapper.putByte(address + 1, (byte) (y * 127.0f));
        UnsafeWrapper.putByte(address + 2, (byte) (z * 127.0f));
    }

    static OptimizedVertexFormatElement generate(VertexFormat format) {
        if (ASSUME_VALID_VERTEX_FORMAT) {
            return generateAssumeValid(format);
        } else {
            return generateChained(format);
        }
    }

    @RequiredArgsConstructor(access = AccessLevel.PROTECTED)
    protected static abstract class AbstractElementClassGenerator<CW extends ClassWriter> {
        public final CW cw;
        public final VertexFormat format;

        protected abstract String internalName();

        protected void visitTex(int tex) {
            //tex(ByteBuffer buffer, int vertexCount, double u, double v)
            MethodVisitor mv = this.cw.visitMethod(ACC_PUBLIC | ACC_FINAL, "tex", Type.getMethodDescriptor(Type.getType(OptimizedVertexFormatElement.class), Type.getType(ByteBuffer.class), Type.INT_TYPE, Type.DOUBLE_TYPE, Type.DOUBLE_TYPE), null, null);
            mv.visitCode();

            this.visitCallRealSetter(mv, tex, "tex", Type.DOUBLE_TYPE, 2);
            this.visitGetNextAndReturn(mv, tex);

            mv.visitMaxs(0, 0);
            mv.visitEnd();
        }

        protected void visitLightmap(int lightmap) {
            //lightmap(ByteBuffer buffer, int vertexCount, int u, int v)
            MethodVisitor mv = this.cw.visitMethod(ACC_PUBLIC | ACC_FINAL, "lightmap", Type.getMethodDescriptor(Type.getType(OptimizedVertexFormatElement.class), Type.getType(ByteBuffer.class), Type.INT_TYPE, Type.INT_TYPE, Type.INT_TYPE), null, null);
            mv.visitCode();

            this.visitCallRealSetter(mv, lightmap, "lightmap", Type.INT_TYPE, 2);
            this.visitGetNextAndReturn(mv, lightmap);

            mv.visitMaxs(0, 0);
            mv.visitEnd();
        }

        protected void visitColor(int color) {
            //color(ByteBuffer buffer, int vertexCount, int r, int g, int b, int a)
            MethodVisitor mv = this.cw.visitMethod(ACC_PUBLIC | ACC_FINAL, "color", Type.getMethodDescriptor(Type.getType(OptimizedVertexFormatElement.class), Type.getType(ByteBuffer.class), Type.INT_TYPE, Type.INT_TYPE, Type.INT_TYPE, Type.INT_TYPE, Type.INT_TYPE), null, null);
            mv.visitCode();

            this.visitCallRealSetter(mv, color, "color", Type.INT_TYPE, 4);
            this.visitGetNextAndReturn(mv, color);

            mv.visitMaxs(0, 0);
            mv.visitEnd();
        }

        protected void visitPos(int pos) {
            //pos(ByteBuffer buffer, int vertexCount, double x, double y, double z)
            MethodVisitor mv = this.cw.visitMethod(ACC_PUBLIC | ACC_FINAL, "pos", Type.getMethodDescriptor(Type.getType(OptimizedVertexFormatElement.class), Type.getType(ByteBuffer.class), Type.INT_TYPE, Type.DOUBLE_TYPE, Type.DOUBLE_TYPE, Type.DOUBLE_TYPE), null, null);
            mv.visitCode();

            this.visitCallRealSetter(mv, pos, "pos", Type.DOUBLE_TYPE, 3);
            this.visitGetNextAndReturn(mv, pos);

            mv.visitMaxs(0, 0);
            mv.visitEnd();
        }

        protected void visitNormal(int normal) {
            //normal(ByteBuffer buffer, int vertexCount, float x, float y, float z)
            MethodVisitor mv = this.cw.visitMethod(ACC_PUBLIC | ACC_FINAL, "normal", Type.getMethodDescriptor(Type.getType(OptimizedVertexFormatElement.class), Type.getType(ByteBuffer.class), Type.INT_TYPE, Type.FLOAT_TYPE, Type.FLOAT_TYPE, Type.FLOAT_TYPE), null, null);
            mv.visitCode();

            this.visitCallRealSetter(mv, normal, "normal", Type.FLOAT_TYPE, 3);
            this.visitGetNextAndReturn(mv, normal);

            mv.visitMaxs(0, 0);
            mv.visitEnd();
        }

        protected void visitNext(int index) {
            //normal()
            MethodVisitor mv = this.cw.visitMethod(ACC_PUBLIC | ACC_FINAL, "next", Type.getMethodDescriptor(Type.getType(OptimizedVertexFormatElement.class)), null, null);
            mv.visitCode();

            this.visitGetNextAndReturn(mv, index);

            mv.visitMaxs(0, 0);
            mv.visitEnd();
        }

        protected void visitCallRealSetter(MethodVisitor mv, int index, String name, Type argumentType, int argumentCount) {
            //UnsafeWrapper.directBufferAddress(buffer) + (vertexCount * format.getSize() + format.getOffset(index))
            //  (parenthesizing it like this seems to result in marginally better codegen)
            mv.visitVarInsn(ALOAD, 1);
            mv.visitMethodInsn(INVOKESTATIC, Type.getInternalName(UnsafeWrapper.class), "directBufferAddress", Type.getMethodDescriptor(Type.LONG_TYPE, Type.getType(Buffer.class)), false);
            mv.visitVarInsn(ILOAD, 2);
            mv.visitInsn(I2L);
            BytecodeHelper.visitLoadConstantInsn(mv, (long) this.format.getSize());
            mv.visitInsn(LMUL);
            BytecodeHelper.visitLoadConstantInsn(mv, (long) this.format.getOffset(index));
            mv.visitInsn(LADD);
            mv.visitInsn(LADD);
            for (int lvt = 3, i = 0; i < argumentCount; lvt += argumentType.getSize(), i++) { //load remaining arguments
                mv.visitVarInsn(argumentType.getOpcode(ILOAD), lvt);
            }
            mv.visitMethodInsn(INVOKESTATIC,
                    Type.getInternalName(OptimizedVertexFormatElement.class),
                    name + signedName(this.format.getElement(index).getType()),
                    Type.getMethodDescriptor(Type.VOID_TYPE,
                            COWArrayUtils.concat(new Type[]{Type.LONG_TYPE}, COWArrayUtils.repeat(argumentCount, argumentType))),
                    false);
        }

        private static String signedName(VertexFormatElement.EnumType type) {
            switch (type) {
                case FLOAT:
                    return "FLOAT";
                case UINT:
                case INT:
                    return "INT";
                case USHORT:
                case SHORT:
                    return "SHORT";
                case UBYTE:
                case BYTE:
                    return "BYTE";
            }
            throw new IllegalArgumentException(String.valueOf(type));
        }

        protected abstract void visitGetNextAndReturn(MethodVisitor mv, int index);

        public AbstractElementClassGenerator<CW> generate() {
            String internalName = this.internalName();

            this.cw.visit(V1_8, ACC_PUBLIC | ACC_FINAL, internalName, null, Type.getInternalName(OptimizedVertexFormatElement.class), null);

            { //constructor
                MethodVisitor mv = this.cw.visitMethod(ACC_PUBLIC, "<init>", Type.getMethodDescriptor(Type.VOID_TYPE), null, null);
                mv.visitCode();

                mv.visitVarInsn(ALOAD, 0);
                mv.visitMethodInsn(INVOKESPECIAL, Type.getInternalName(OptimizedVertexFormatElement.class), "<init>", Type.getMethodDescriptor(Type.VOID_TYPE), false);
                mv.visitInsn(RETURN);

                mv.visitMaxs(0, 0);
                mv.visitEnd();
            }

            OptimizedVertexFormat.visitVertexSizeGetters(this.cw, this.format);
            this.visitAttributes();

            this.cw.visitEnd();
            PPatchesTransformerRoot.dumpClass(internalName, this.cw);

            return this;
        }

        protected abstract void visitAttributes();

        public abstract Class<?> defineClass();
    }

    @SneakyThrows
    private static OptimizedVertexFormatElement generateAssumeValid(VertexFormat format) {
        Class<?> clazz = new AssumeValidClassGenerator(format).generate().defineClass();
        return (OptimizedVertexFormatElement) MethodHandles.publicLookup().findConstructor(clazz, MethodType.methodType(void.class)).invoke();
    }

    protected static final class AssumeValidClassGenerator extends AbstractElementClassGenerator<AnonymousClassWriter> {
        public AssumeValidClassGenerator(VertexFormat format) {
            super(AnonymousClassWriter.create(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES), format);
        }

        @Override
        protected String internalName() {
            return Type.getInternalName(OptimizedVertexFormatElement.class) + "AssumeValidImpl/" + OptimizedVertexFormat.mangleVertexFormat(this.format);
        }

        @Override
        protected void visitAttributes() {
            int tex = -1;
            int lightmap = -1;
            int color = -1;
            int pos = -1;
            int normal = -1;
            for (int i = 0; i < this.format.getElementCount(); i++) {
                VertexFormatElement element = this.format.getElement(i);
                int index = element.getIndex();
                switch (element.getUsage()) {
                    case UV:
                        switch (index) {
                            case 0:
                                Preconditions.checkArgument(tex < 0, "vertex format contains multiple tex elements: %s", this.format);
                                tex = i;
                                break;
                            case 1:
                                Preconditions.checkArgument(lightmap < 0, "vertex format contains multiple lightmap elements: %s", this.format);
                                lightmap = i;
                                break;
                            default:
                                throw new IllegalArgumentException("vertex format contains UV element with unsupported index: " + index);
                        }
                        break;
                    case COLOR:
                        Preconditions.checkArgument(color < 0, "vertex format contains multiple color elements: %s", this.format);
                        Preconditions.checkArgument(index == 0, "vertex format contains color element with unsupported index: %s", index);
                        color = i;
                        break;
                    case POSITION:
                        Preconditions.checkArgument(pos < 0, "vertex format contains multiple pos elements: %s", this.format);
                        Preconditions.checkArgument(index == 0, "vertex format contains pos element with unsupported index: %s", index);
                        pos = i;
                        break;
                    case NORMAL:
                        Preconditions.checkArgument(normal < 0, "vertex format contains multiple normal elements: %s", this.format);
                        Preconditions.checkArgument(index == 0, "vertex format contains normal element with unsupported index: %s", index);
                        normal = i;
                        break;
                    case PADDING:
                        continue;
                    default:
                        throw new IllegalArgumentException(element.getUsage().name());
                }
            }

            if (tex >= 0) {
                this.visitTex(tex);
            }

            if (lightmap >= 0) {
                this.visitLightmap(lightmap);
            }

            if (color >= 0) {
                this.visitColor(color);
            }

            if (pos >= 0) {
                this.visitPos(pos);
            }

            if (normal >= 0) {
                this.visitNormal(normal);
            }

            this.visitNext(-1);
        }

        @Override
        protected void visitGetNextAndReturn(MethodVisitor mv, int index) {
            mv.visitInsn(ACONST_NULL);
            mv.visitInsn(ARETURN);
        }

        @Override
        public Class<?> defineClass() {
            return this.cw.defineAnonymousClass(OptimizedVertexFormatElement.class);
        }
    }

    private static OptimizedVertexFormatElement generateChained(VertexFormat format) {
        return new ChainedInstancesClassLoader(format).optimizedElements[0];
    }

    public static final class ChainedInstancesClassLoader extends ClassLoader {
        final ImmutableList<VertexFormatElement> vanillaElements;
        final OptimizedVertexFormatElement[] optimizedElements;

        @SneakyThrows
        public ChainedInstancesClassLoader(VertexFormat format) {
            super(ChainedInstancesClassLoader.class.getClassLoader());
            this.vanillaElements = ImmutableList.copyOf(format.getElements());
            this.optimizedElements = new OptimizedVertexFormatElement[this.vanillaElements.size()];

            for (int i = 0; i < this.optimizedElements.length; i++) {
                Class<?> clazz = new ChainedInstancesClassGenerator(format, this, i).generate().defineClass();
                this.optimizedElements[i] = (OptimizedVertexFormatElement) MethodHandles.publicLookup().findConstructor(clazz, MethodType.methodType(void.class)).invoke();
            }
        }

        public static CallSite bootstrapGetNextElement(MethodHandles.Lookup lookup, String name, MethodType type, int index) {
            ChainedInstancesClassLoader classLoader = (ChainedInstancesClassLoader) lookup.lookupClass().getClassLoader();

            int nextIndex = index;
            do {
                nextIndex++;
                nextIndex %= classLoader.optimizedElements.length;
            } while (classLoader.vanillaElements.get(nextIndex).getUsage() == VertexFormatElement.EnumUsage.PADDING);

            return new ConstantCallSite(MethodHandles.constant(type.returnType(), Objects.requireNonNull(classLoader.optimizedElements[nextIndex])));
        }
    }

    protected static final class ChainedInstancesClassGenerator extends AbstractElementClassGenerator<ClassWriter> {
        private final ChainedInstancesClassLoader loader;
        private final int index;

        public ChainedInstancesClassGenerator(VertexFormat format, ChainedInstancesClassLoader loader, int index) {
            super(new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES), format);
            this.loader = loader;
            this.index = index;
        }

        @Override
        protected String internalName() {
            return Type.getInternalName(OptimizedVertexFormatElement.class) + "ChainedInstancesImpl/" + OptimizedVertexFormat.mangleVertexFormat(this.format) + "/Elt" + this.index;
        }

        @Override
        protected void visitAttributes() {
            this.visitTex(this.index);
            this.visitLightmap(this.index);
            this.visitColor(this.index);
            this.visitPos(this.index);
            this.visitNormal(this.index);
            this.visitNext(this.index);
        }

        @Override
        protected void visitGetNextAndReturn(MethodVisitor mv, int index) {
            mv.visitInvokeDynamicInsn("next", Type.getMethodDescriptor(Type.getType(OptimizedVertexFormatElement.class)),
                    new Handle(H_INVOKESTATIC,
                            Type.getInternalName(OptimizedVertexFormatElement.ChainedInstancesClassLoader.class),
                            "bootstrapGetNextElement",
                            Type.getMethodDescriptor(Type.getType(CallSite.class), Type.getType(MethodHandles.Lookup.class), Type.getType(String.class), Type.getType(MethodType.class), Type.INT_TYPE),
                            false),
                    index);
            mv.visitInsn(ARETURN);
        }

        @Override
        public Class<?> defineClass() {
            return UnsafeWrapper.defineClass(this.internalName().replace('/', '.'), this.cw.toByteArray(), this.loader);
        }
    }
}
