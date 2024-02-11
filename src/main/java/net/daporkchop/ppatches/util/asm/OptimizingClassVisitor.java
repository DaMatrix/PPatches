package net.daporkchop.ppatches.util.asm;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;

import static org.objectweb.asm.Opcodes.*;

/**
 * A {@link ClassVisitor} which can apply some very simple size optimizations to bytecode.
 *
 * @author DaPorkchop_
 */
public class OptimizingClassVisitor extends ClassVisitor {
    public OptimizingClassVisitor(ClassVisitor cv) {
        super(ASM5, cv);
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
        return new OptimizingMethodVisitor(super.visitMethod(access, name, desc, signature, exceptions));
    }

    /**
     * A {@link MethodVisitor} which can apply some very simple size optimizations to bytecode.
     *
     * @author DaPorkchop_
     */
    public static class OptimizingMethodVisitor extends MethodVisitor {
        public OptimizingMethodVisitor(MethodVisitor mv) {
            super(ASM5, mv);
        }

        @Override
        public void visitLdcInsn(Object cst) {
            //try to simplify LDC instructions which could be expressed using a more compact opcode
            if (cst instanceof Integer) {
                int intVal = (Integer) cst;
                if (intVal >= -1 && intVal <= 5) {
                    super.visitInsn(ICONST_0 + intVal);
                    return;
                } else if (intVal >= Byte.MIN_VALUE && intVal <= Byte.MAX_VALUE) {
                    super.visitIntInsn(BIPUSH, intVal);
                    return;
                } else if (intVal >= Short.MIN_VALUE && intVal <= Short.MAX_VALUE) {
                    super.visitIntInsn(SIPUSH, intVal);
                    return;
                }
            } else if (cst instanceof Long) {
                long longVal = (Long) cst;
                if (longVal >= 0L && longVal <= 1L) {
                    super.visitInsn(LCONST_0 + (int) longVal);
                    return;
                }
            } else if (cst instanceof Float) {
                int floatBits = Float.floatToRawIntBits((Float) cst);
                if (floatBits == Float.floatToRawIntBits(0.0f)) {
                    super.visitInsn(FCONST_0);
                    return;
                } else if (floatBits == Float.floatToRawIntBits(1.0f)) {
                    super.visitInsn(FCONST_1);
                    return;
                } else if (floatBits == Float.floatToRawIntBits(2.0f)) {
                    super.visitInsn(FCONST_2);
                    return;
                }
            } else if (cst instanceof Double) {
                long doubleBits = Double.doubleToRawLongBits((Double) cst);
                if (doubleBits == Double.doubleToRawLongBits(0.0f)) {
                    super.visitInsn(DCONST_0);
                    return;
                } else if (doubleBits == Double.doubleToRawLongBits(1.0f)) {
                    super.visitInsn(DCONST_1);
                    return;
                }
            }
            super.visitLdcInsn(cst);
        }
    }
}
