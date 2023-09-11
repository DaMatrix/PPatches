package net.daporkchop.ppatches.util.asm;

import com.google.common.collect.ImmutableList;
import lombok.RequiredArgsConstructor;
import net.daporkchop.ppatches.util.asm.analysis.IDataflowProvider;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;

import java.util.*;

import static org.objectweb.asm.Opcodes.*;

/**
 * @author DaPorkchop_
 */
public class VarargsParameterDecoder {
    public static Optional<Result> tryDecode(AbstractInsnNode consumingInsn, IDataflowProvider dataflowProvider) {
        AbstractInsnNode newArrayInsn = dataflowProvider.getSingleStackOperandSourceFromTop(consumingInsn, 0);
        Type arrayType;
        if (newArrayInsn == null || newArrayInsn.getOpcode() == MULTIANEWARRAY || (arrayType = BytecodeHelper.decodeNewArrayType(newArrayInsn).orElse(null)) == null) {
            return Optional.empty();
        }
        Type elementType = arrayType.getElementType();

        AbstractInsnNode loadLengthInsn = dataflowProvider.getSingleStackOperandSourceFromBottom(newArrayInsn, 0);
        Object rawConstantLength;
        if (loadLengthInsn == null || !((rawConstantLength = BytecodeHelper.decodeConstant(loadLengthInsn)) instanceof Integer)) {
            return Optional.empty();
        }
        int constantLength = (Integer) rawConstantLength;

        @SuppressWarnings("unchecked")
        List<Element> elements = (List<Element>) (Object) Arrays.asList(new Object[constantLength]);

        for (AbstractInsnNode dupInsn : dataflowProvider.getSoleResultStackUsages(newArrayInsn).insns) {
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

            AbstractInsnNode astoreInsn = dataflowProvider.getSoleResultSingleStackUsage(dupInsn);
            if (astoreInsn == null || astoreInsn.getOpcode() != elementType.getOpcode(IASTORE)) {
                //the DUP result isn't consumed by a single *ASTORE instruction
                return Optional.empty();
            }

            assert dataflowProvider.getSingleStackOperandSourceFromBottom(astoreInsn, 1) == loadIndexInsn
                   && dataflowProvider.getSingleStackOperandSourceFromBottom(astoreInsn, 0) == dupInsn;

            elements.set(constantIndex, new Element(dupInsn, loadIndexInsn, dataflowProvider.getStackOperandSourcesFromTop(astoreInsn, 2).insns, astoreInsn));
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
