package net.daporkchop.ppatches.util.asm;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.VarInsnNode;

/**
 * @author DaPorkchop_
 */
@EqualsAndHashCode
public final class LVTReference {
    private final int loadOpcode;
    private final int storeOpcode;

    @Getter
    private final int var;

    public LVTReference(Type type, int var) {
        this.loadOpcode = type.getOpcode(Opcodes.ILOAD);
        this.storeOpcode = type.getOpcode(Opcodes.ISTORE);
        this.var = var;
    }

    public void visitLoad(MethodVisitor mv) {
        mv.visitVarInsn(this.loadOpcode, this.var);
    }

    public VarInsnNode makeLoad() {
        return new VarInsnNode(this.loadOpcode, this.var);
    }

    public void visitStore(MethodVisitor mv) {
        mv.visitVarInsn(this.storeOpcode, this.var);
    }

    public VarInsnNode makeStore() {
        return new VarInsnNode(this.storeOpcode, this.var);
    }

    @FunctionalInterface
    public interface Allocator {
        LVTReference allocate(Type type);
    }
}
