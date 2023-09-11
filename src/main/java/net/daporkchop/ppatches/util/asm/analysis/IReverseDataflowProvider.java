package net.daporkchop.ppatches.util.asm.analysis;

import net.daporkchop.ppatches.util.asm.BytecodeHelper;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.analysis.Frame;
import org.objectweb.asm.tree.analysis.SourceValue;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * @author DaPorkchop_
 */
public interface IReverseDataflowProvider {
    static IReverseDataflowProvider fromSourceAnalyzer(MethodNode methodNode, Frame<SourceValue>[] sourceFrames) {
        return new IReverseDataflowProvider() {
            @Override
            public boolean isUnreachable(AbstractInsnNode insn) {
                return sourceFrames[methodNode.instructions.indexOf(insn)] == null;
            }

            @Override
            public List<SourceValue> getStackOperandSources(AbstractInsnNode insn) {
                Frame<SourceValue> sourceFrame = sourceFrames[methodNode.instructions.indexOf(insn)];

                int consumedStackOperands = BytecodeHelper.getConsumedStackOperandCount(insn, sourceFrame);
                List<SourceValue> result = new ArrayList<>(consumedStackOperands);
                for (int i = consumedStackOperands - 1; i >= 0; i--) {
                    result.add(BytecodeHelper.getStackValueFromTop(sourceFrame, i));
                }
                return result;
            }

            @Override
            public SourceValue getLocalSources(AbstractInsnNode insn, int localIndex) {
                return sourceFrames[methodNode.instructions.indexOf(insn)].getLocal(localIndex);
            }
        };
    }

    boolean isUnreachable(AbstractInsnNode insn);

    List<SourceValue> getStackOperandSources(AbstractInsnNode insn);

    default SourceValue getStackOperandSourcesFromBottom(AbstractInsnNode insn, int indexFromBottom) throws IndexOutOfBoundsException {
        return this.getStackOperandSources(insn).get(indexFromBottom);
    }

    default SourceValue getStackOperandSourcesFromTop(AbstractInsnNode insn, int indexFromTop) throws IndexOutOfBoundsException {
        List<SourceValue> consumedStackOperands = this.getStackOperandSources(insn);
        return consumedStackOperands.get(consumedStackOperands.size() - 1 - indexFromTop);
    }

    @Nullable
    default AbstractInsnNode getSingleStackOperandSourceFromBottom(AbstractInsnNode insn, int indexFromBottom) throws IndexOutOfBoundsException {
        Set<AbstractInsnNode> sources = this.getStackOperandSourcesFromBottom(insn, indexFromBottom).insns;
        return sources.size() == 1 ? sources.iterator().next() : null;
    }

    @Nullable
    default AbstractInsnNode getSingleStackOperandSourceFromTop(AbstractInsnNode insn, int indexFromTop) throws IndexOutOfBoundsException {
        Set<AbstractInsnNode> sources = this.getStackOperandSourcesFromTop(insn, indexFromTop).insns;
        return sources.size() == 1 ? sources.iterator().next() : null;
    }

    SourceValue getLocalSources(AbstractInsnNode insn, int localIndex);

    @Nullable
    default AbstractInsnNode getSingleLocalSource(AbstractInsnNode insn, int localIndex) {
        Set<AbstractInsnNode> sources = this.getLocalSources(insn, localIndex).insns;
        return sources.size() == 1 ? sources.iterator().next() : null;
    }
}
