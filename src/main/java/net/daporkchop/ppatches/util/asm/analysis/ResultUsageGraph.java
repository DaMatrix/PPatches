package net.daporkchop.ppatches.util.asm.analysis;

import it.unimi.dsi.fastutil.objects.ReferenceArraySet;
import lombok.RequiredArgsConstructor;
import net.daporkchop.ppatches.util.asm.BytecodeHelper;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.analysis.Frame;
import org.objectweb.asm.tree.analysis.SourceValue;

import java.util.Collections;
import java.util.Objects;
import java.util.Set;

/**
 * @author DaPorkchop_
 */
@RequiredArgsConstructor
public class ResultUsageGraph {
    private final MethodNode methodNode;
    private final Set<AbstractInsnNode>[] usages;

    public ResultUsageGraph(MethodNode methodNode, Frame<SourceValue>[] sources) {
        this.methodNode = methodNode;

        Set<AbstractInsnNode> emptySet = Collections.emptySet();

        @SuppressWarnings("unchecked")
        Set<AbstractInsnNode>[] usagesBySourceInsnIndex = (Set<AbstractInsnNode>[]) new Set[sources.length];

        //set an initially empty set of usages for all reachable instructions
        for (int i = 0; i < sources.length; i++) {
            if (sources[i] != null) { //the instruction is reachable
                usagesBySourceInsnIndex[i] = emptySet;
            }
        }

        //iterate over all reachable instructions, and for each potential source instruction for each stack operand consumed by the instruction, add the consuming instruction as a potential user
        //  of the source instruction's produced value
        for (int i = 0; i < sources.length; i++) {
            if (sources[i] != null //the instruction is reachable
                && BytecodeHelper.isNormalCodeInstruction(methodNode.instructions.get(i))) { //ignore labels, frames and line numbers
                AbstractInsnNode consumingInsn = methodNode.instructions.get(i);
                Frame<SourceValue> consumingInsnSourceFrame = sources[i];

                //iterate over all the stack operands consumed by this instruction
                for (int consumedOperandIndex = BytecodeHelper.getConsumedStackOperandCount(consumingInsn, consumingInsnSourceFrame) - 1; consumedOperandIndex >= 0; consumedOperandIndex--) {
                    //iterate over all instructions which could produce this stack operand
                    for (AbstractInsnNode producingInsn : BytecodeHelper.getStackValueFromTop(consumingInsnSourceFrame, consumedOperandIndex).insns) {
                        int producingInsnIndex = methodNode.instructions.indexOf(producingInsn);

                        //add the consuming instruction to the source instruction's usage set
                        Set<AbstractInsnNode> usages = usagesBySourceInsnIndex[producingInsnIndex];
                        if (usages == emptySet) {
                            usagesBySourceInsnIndex[producingInsnIndex] = Collections.singleton(consumingInsn);
                        } else {
                            if (!(usages instanceof ReferenceArraySet)) { //the set has a size of 1, let's convert it to a ReferenceArraySet
                                Set<AbstractInsnNode> newUsages = new ReferenceArraySet<>(usages.size() + 2);
                                usagesBySourceInsnIndex[producingInsnIndex] = newUsages;
                                newUsages.addAll(usages);
                                usages = newUsages;
                            }

                            //usages is already a ReferenceArraySet, we can just add to it
                            usages.add(consumingInsn);
                        }
                    }
                }
            }
        }

        //assert that everything is exactly the same as the reference implementation which computes one value at a time
        for (int i = 0; i < sources.length; i++) {
            assert usagesBySourceInsnIndex[i] == null || Objects.equals(usagesBySourceInsnIndex[i], BytecodeHelper.analyzeUsages(null, methodNode, sources, methodNode.instructions.get(i)));
        }

        this.usages = usagesBySourceInsnIndex;
    }

    /**
     * Gets a {@link Set} containing every instruction which could use the value produced by the given instruction.
     *
     * @param producingInsn an instruction
     * @return a {@link Set} containing every instruction which could use the value produced by the given instruction, or {@code null} if the given instruction isn't reachable
     */
    public Set<AbstractInsnNode> getUsages(AbstractInsnNode producingInsn) {
        return this.usages[this.methodNode.instructions.indexOf(producingInsn)];
    }
}
