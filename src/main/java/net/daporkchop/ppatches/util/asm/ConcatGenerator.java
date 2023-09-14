package net.daporkchop.ppatches.util.asm;

import net.daporkchop.ppatches.util.asm.concat.AppendStringBuilderOptimizationRegistry;
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

    public InsnList begin() {
        return BytecodeHelper.makeInsnList(
                new TypeInsnNode(NEW, "java/lang/StringBuilder"),
                new InsnNode(DUP),
                new MethodInsnNode(INVOKESPECIAL, "java/lang/StringBuilder", "<init>", "()V", false));
    }

    public InsnList append(Type type) {
        this.prevConstantValueInsn = null;
        return AppendStringBuilderOptimizationRegistry.makeAppend(type);
    }

    public InsnList appendConstant(String cst) {
        if (this.prevConstantValueInsn != null) {
            this.prevConstantValueInsn.cst = this.prevConstantValueInsn.cst + cst;
            return BytecodeHelper.makeInsnList();
        }
        this.prevConstantValueInsn = new LdcInsnNode(cst);
        return BytecodeHelper.makeInsnList(this.prevConstantValueInsn, new MethodInsnNode(INVOKEVIRTUAL, "java/lang/StringBuilder", "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;", false));
    }

    public InsnList finish() {
        this.prevConstantValueInsn = null;
        return BytecodeHelper.makeInsnList(new MethodInsnNode(INVOKEVIRTUAL, "java/lang/StringBuilder", "toString", "()Ljava/lang/String;", false));
    }
}
