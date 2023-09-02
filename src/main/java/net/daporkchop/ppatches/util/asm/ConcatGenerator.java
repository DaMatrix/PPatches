package net.daporkchop.ppatches.util.asm;

import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;

import static org.objectweb.asm.Opcodes.*;

/**
 * Helper class for generating string concatenations.
 *
 * @author DaPorkchop_
 */
public final class ConcatGenerator {
    private LdcInsnNode prevConstantValueInsn;
    private MethodInsnNode prevAppendInsn;

    public InsnList begin() {
        return BytecodeHelper.makeInsnList(
                new TypeInsnNode(NEW, "java/lang/StringBuilder"),
                new InsnNode(DUP),
                new MethodInsnNode(INVOKESPECIAL, "java/lang/StringBuilder", "<init>", "()V", false));
    }

    public InsnList append(Type type) {
        String desc = type.getDescriptor();
        if ((type.getSort() == Type.OBJECT || type.getSort() == Type.ARRAY) && !"Ljava/lang/String;".equals(desc)) {
            desc = "Ljava/lang/Object;";
        }

        this.prevConstantValueInsn = null;
        this.prevAppendInsn = new MethodInsnNode(INVOKEVIRTUAL, "java/lang/StringBuilder", "append", '(' + desc + ")Ljava/lang/StringBuilder;", false);
        return BytecodeHelper.makeInsnList(this.prevAppendInsn);
    }

    public InsnList appendConstant(String cst) {
        if (this.prevConstantValueInsn != null) {
            this.prevConstantValueInsn.cst = this.prevConstantValueInsn.cst + cst;
            return BytecodeHelper.makeInsnList();
        }
        this.prevConstantValueInsn = new LdcInsnNode(cst);
        this.prevAppendInsn = new MethodInsnNode(INVOKEVIRTUAL, "java/lang/StringBuilder", "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;", false);
        return BytecodeHelper.makeInsnList(this.prevConstantValueInsn, this.prevAppendInsn);
    }

    public InsnList finish() {
        this.prevConstantValueInsn = null;
        this.prevAppendInsn = null;
        return BytecodeHelper.makeInsnList(new MethodInsnNode(INVOKEVIRTUAL, "java/lang/StringBuilder", "toString", "()Ljava/lang/String;", false));
    }
}
