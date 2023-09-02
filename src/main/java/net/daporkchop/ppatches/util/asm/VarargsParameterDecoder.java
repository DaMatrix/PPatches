package net.daporkchop.ppatches.util.asm;

import com.google.common.collect.ImmutableList;
import lombok.RequiredArgsConstructor;
import net.daporkchop.ppatches.util.asm.analysis.ResultUsageGraph;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.analysis.Frame;
import org.objectweb.asm.tree.analysis.SourceValue;
import scala.actors.threadpool.Arrays;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.objectweb.asm.Opcodes.*;

/**
 * @author DaPorkchop_
 */
public class VarargsParameterDecoder {
    public static Optional<Result> tryDecode(String ownerName, MethodNode methodNode, AbstractInsnNode consumingInsn) {
        return tryDecode(ownerName, methodNode, consumingInsn, BytecodeHelper.analyzeSources(ownerName, methodNode));
    }

    public static Optional<Result> tryDecode(String ownerName, MethodNode methodNode, AbstractInsnNode consumingInsn, Frame<SourceValue>[] sourceFrames) {
        return tryDecode(ownerName, methodNode, consumingInsn, sourceFrames, BytecodeHelper.analyzeUsages(ownerName, methodNode, sourceFrames));
    }

    public static Optional<Result> tryDecode(String ownerName, MethodNode methodNode, AbstractInsnNode consumingInsn, Frame<SourceValue>[] sourceFrames, ResultUsageGraph usageGraph) {
        AbstractInsnNode newArrayInsn = BytecodeHelper.getSingleSourceInsnFromTop(methodNode, sourceFrames, consumingInsn, 0);
        Type arrayType;
        if (newArrayInsn == null || newArrayInsn.getOpcode() == MULTIANEWARRAY || (arrayType = BytecodeHelper.decodeNewArrayType(newArrayInsn).orElse(null)) == null) {
            return Optional.empty();
        }
        Type elementType = arrayType.getElementType();

        AbstractInsnNode loadLengthInsn = BytecodeHelper.getSingleSourceInsnFromTop(methodNode, sourceFrames, newArrayInsn, 0);
        Object rawConstantLength;
        if (loadLengthInsn == null || !((rawConstantLength = BytecodeHelper.decodeConstant(loadLengthInsn)) instanceof Integer)) {
            return Optional.empty();
        }
        int constantLength = (Integer) rawConstantLength;

        @SuppressWarnings("unchecked")
        List<Element> elements = (List<Element>) Arrays.asList(new Object[constantLength]);

        for (AbstractInsnNode dupInsn : usageGraph.getUsages(newArrayInsn)) {
            if (dupInsn == consumingInsn) { //the final instruction consuming the array is also a "usage", which we want to skip
                continue;
            } else if (dupInsn.getOpcode() != DUP) {
                return Optional.empty();
            }

            AbstractInsnNode loadIndexInsn = dupInsn.getNext();
            Object rawConstantIndex = BytecodeHelper.decodeConstant(loadIndexInsn);
            int constantIndex;
            if (!(rawConstantIndex instanceof Integer) || elements.get(constantIndex = (Integer) rawConstantIndex) != null) {
                return Optional.empty();
            }

            AbstractInsnNode astoreInsn;
            if (usageGraph.getUsages(dupInsn).size() != 1 || (astoreInsn = usageGraph.getUsages(dupInsn).iterator().next()).getOpcode() != elementType.getOpcode(IASTORE)) {
                //the DUP result isn't consumed by a single *ASTORE instruction
                return Optional.empty();
            }

            assert BytecodeHelper.getSingleSourceInsnFromTop(methodNode, sourceFrames, astoreInsn, 1) == loadIndexInsn
                   && BytecodeHelper.getSingleSourceInsnFromTop(methodNode, sourceFrames, astoreInsn, 2) == dupInsn;

            elements.set(constantIndex, new Element(dupInsn, loadIndexInsn, BytecodeHelper.getStackValueFromTop(methodNode, sourceFrames, astoreInsn, 0).insns, astoreInsn));
        }

        if (elements.contains(null)) { //at least one element wasn't set
            return Optional.empty();
        }

        return Optional.of(new Result(loadLengthInsn, newArrayInsn, elements));
    }

    /**
     * @author DaPorkchop_
     */
    @RequiredArgsConstructor
    public static final class Result {
        public final AbstractInsnNode loadLengthInsn;
        public final AbstractInsnNode newArrayInsn;
        public final List<Element> elements;

        public ImmutableList<AbstractInsnNode> beforeElementsInsns() {
            return ImmutableList.of(this.loadLengthInsn, this.newArrayInsn);
        }
    }

    /**
     * @author DaPorkchop_
     */
    @RequiredArgsConstructor
    public static final class Element {
        public final AbstractInsnNode dupInsn;
        public final AbstractInsnNode loadIndexInsn;
        public final Set<AbstractInsnNode> valueSourceInsns;
        public final AbstractInsnNode astoreInsn;

        public ImmutableList<AbstractInsnNode> beforeValueInsns() {
            return ImmutableList.of(this.dupInsn, this.loadIndexInsn);
        }

        public ImmutableList<AbstractInsnNode> afterValueInsns() {
            return ImmutableList.of(this.astoreInsn);
        }
    }
}
