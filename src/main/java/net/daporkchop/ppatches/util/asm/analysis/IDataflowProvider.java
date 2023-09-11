package net.daporkchop.ppatches.util.asm.analysis;

import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.analysis.SourceValue;

import java.util.List;

/**
 * @author DaPorkchop_
 */
public interface IDataflowProvider extends IReverseDataflowProvider, IForwardDataflowProvider {
    static IDataflowProvider from(IReverseDataflowProvider reverse, IForwardDataflowProvider forward) {
        return new IDataflowProvider() {
            @Override
            public boolean isUnreachable(AbstractInsnNode insn) {
                return reverse.isUnreachable(insn);
            }

            @Override
            public List<UsageValue> getStackUsages(AbstractInsnNode insn) {
                return forward.getStackUsages(insn);
            }

            @Override
            public UsageValue getLocalUsages(AbstractInsnNode insn) {
                return forward.getLocalUsages(insn);
            }

            @Override
            public List<SourceValue> getStackOperandSources(AbstractInsnNode insn) {
                return reverse.getStackOperandSources(insn);
            }

            @Override
            public SourceValue getStackOperandSourcesFromBottom(AbstractInsnNode insn, int indexFromBottom) throws IndexOutOfBoundsException {
                return reverse.getStackOperandSourcesFromBottom(insn, indexFromBottom);
            }

            @Override
            public SourceValue getStackOperandSourcesFromTop(AbstractInsnNode insn, int indexFromTop) throws IndexOutOfBoundsException {
                return reverse.getStackOperandSourcesFromTop(insn, indexFromTop);
            }

            @Override
            public SourceValue getLocalSources(AbstractInsnNode insn, int localIndex) {
                return reverse.getLocalSources(insn, localIndex);
            }
        };
    }
}
