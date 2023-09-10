package net.daporkchop.ppatches.util.asm.analysis;

import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.analysis.SourceValue;

import java.util.List;

/**
 * @author DaPorkchop_
 */
public interface IReverseDataflowProvider {
    List<SourceValue> getConsumedStackOperands(AbstractInsnNode insn);

    default SourceValue getConsumedStackOperandFromTop(AbstractInsnNode insn, int indexFromTop) {
        List<SourceValue> consumedStackOperands = this.getConsumedStackOperands(insn);
        return consumedStackOperands.get(consumedStackOperands.size() - 1 - indexFromTop);
    }
}
