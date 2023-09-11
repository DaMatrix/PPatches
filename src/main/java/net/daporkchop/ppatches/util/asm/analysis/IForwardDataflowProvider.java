package net.daporkchop.ppatches.util.asm.analysis;

import com.google.common.base.Preconditions;
import org.objectweb.asm.tree.AbstractInsnNode;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Set;

/**
 * @author DaPorkchop_
 */
public interface IForwardDataflowProvider {
    boolean isUnreachable(AbstractInsnNode insn);

    /**
     * Gets a {@link List} containing a {@link UsageValue} containing every instruction which could use each stack value produced by the given instruction.
     *
     * @param insn an instruction
     * @return a {@link List} containing a {@link UsageValue} containing every instruction which could use each stack value produced by the given instruction
     */
    List<UsageValue> getStackUsages(AbstractInsnNode insn);

    /**
     * Gets a {@link UsageValue} containing every instruction which could use the stack value produced by the given instruction, under the assumption that the given
     * instruction produces exactly one stack value.
     *
     * @param insn an instruction
     * @return a {@link UsageValue} containing every instruction which could use the stack value produced by the given instruction
     * @throws IllegalStateException if the given instruction doesn't produce exactly one stack value
     */
    default UsageValue getSoleResultStackUsages(AbstractInsnNode insn) throws IllegalStateException {
        List<UsageValue> usages = this.getStackUsages(insn);
        Preconditions.checkState(usages.size() == 1, "instruction doesn't have exactly one result ", insn);
        return usages.get(0);
    }

    /**
     * Gets the single instruction which could use the stack value produced by the given instruction, under the assumption that the given
     * instruction produces exactly one stack value.
     *
     * @param insn an instruction
     * @return the single instruction which could use the stack value produced by the given instruction, or {@code null} if the produced stack value isn't used by exactly one instruction
     * @throws IllegalStateException if the given instruction doesn't produce exactly one stack value
     */
    @Nullable
    default AbstractInsnNode getSoleResultSingleStackUsage(AbstractInsnNode insn) throws IllegalStateException {
        Set<AbstractInsnNode> usages = this.getSoleResultStackUsages(insn).insns;
        return usages.size() == 1 ? usages.iterator().next() : null;
    }

    /**
     * Gets a {@link UsageValue} containing every instruction which could use the local value produced by the given instruction.
     *
     * @param insn an instruction
     * @return a {@link UsageValue} containing every instruction which could use the local value produced by the given instruction, or {@code null} if the given instruction doesn't store any values to the LVT
     */
    UsageValue getLocalUsages(AbstractInsnNode insn);

    /**
     * Gets the single instruction which could use the local value produced by the given instruction.
     *
     * @param insn an instruction
     * @return the single instruction which could use the local value produced by the given instruction, or {@code null} if the given instruction doesn't store any values to the LVT, or if the produced local value isn't used by exactly one instruction
     */
    @Nullable
    default AbstractInsnNode getSingleLocalUsage(AbstractInsnNode insn) {
        UsageValue usages = this.getLocalUsages(insn);
        return usages != null && usages.insns.size() == 1 ? usages.insns.iterator().next() : null;
    }
}
