package net.daporkchop.ppatches.util.asm.analysis;

import lombok.SneakyThrows;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.*;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * @author DaPorkchop_
 */
//using raw types to prevent bridges
public final class ReachabilityAnalyzer extends Analyzer {
    /*
     * This is abusing Analyzer's ability to determine instruction reachability without using any of its other features. To make this faster, we have a single
     * Value instance with a size of 1 and a single Frame instance which always returns a value for reads and discards all writes. The only useful information
     * generated is whether or not an element in the resulting Frame[] is null.
     */

    @SuppressWarnings("unchecked")
    private ReachabilityAnalyzer() {
        super(ReachabilityInterpreter.INSTANCE);
    }

    @Override
    protected Frame newFrame(int nLocals, int nStack) {
        return ReachabilityFrame.INSTANCE;
    }

    @Override
    protected Frame newFrame(Frame src) {
        return ReachabilityFrame.INSTANCE;
    }

    @SneakyThrows(AnalyzerException.class)
    public static void removeUnreachableInstructions(String ownerName, MethodNode methodNode) {
        //quickly analyze the code to find unreachable instructions
        Frame[] frames = new ReachabilityAnalyzer().analyze(ownerName, methodNode);

        //remove all instructions which aren't reachable (their frame is null)
        InsnList instructions = methodNode.instructions;

        boolean shouldCheckLabels = !methodNode.tryCatchBlocks.isEmpty() || methodNode.localVariables != null || methodNode.visibleLocalVariableAnnotations != null || methodNode.invisibleLocalVariableAnnotations != null;
        List<LabelNode> removedLabels = null;

        int i = 0;
        for (AbstractInsnNode insn = instructions.getFirst(), next; insn != null; insn = next, i++) {
            next = insn.getNext();
            if (frames[i] == null) {
                if (shouldCheckLabels && insn instanceof LabelNode && insn == instructions.getLast()) {
                    //don't remove the final LabelNode in the instruction, as doing so will result in pretty much all local variable information being deleted
                    //TODO: there are still some labels which would need to be kept, for instance to preserve local variable information about locals which go out of
                    // scope from a return instruction which isn't at the tail of the function
                    continue;
                }

                instructions.remove(insn);

                if (shouldCheckLabels && insn instanceof LabelNode) {
                    if (removedLabels == null) {
                        removedLabels = new ArrayList<>();
                    }
                    removedLabels.add((LabelNode) insn);
                }
            }
        }

        //some labels were removed, we should post-process them
        if (removedLabels != null) {
            postProcessMethodWithRemovedLabels(methodNode, removedLabels);
        }
    }

    private static void postProcessMethodWithRemovedLabels(MethodNode methodNode, List<LabelNode> removedLabels) {
        if (!methodNode.tryCatchBlocks.isEmpty()) {
            methodNode.tryCatchBlocks.removeIf(tcb -> removedLabels.contains(tcb.start) || removedLabels.contains(tcb.end) || removedLabels.contains(tcb.handler));
        }

        if (methodNode.localVariables != null) {
            methodNode.localVariables.removeIf(localVariable -> removedLabels.contains(localVariable.start) || removedLabels.contains(localVariable.end));
            if (methodNode.localVariables.isEmpty()) {
                methodNode.localVariables = null;
            }
        }

        if (methodNode.visibleLocalVariableAnnotations != null || methodNode.invisibleLocalVariableAnnotations != null) {
            Predicate<LocalVariableAnnotationNode> filter = lvan -> {
                for (LabelNode removedLabel : removedLabels) {
                    //if the label was used as a start or end label, remove the entire item with that index
                    for (int i; (i = lvan.start.indexOf(removedLabel)) >= 0; ) {
                        lvan.start.remove(i);
                        lvan.end.remove(i);
                        lvan.index.remove(i);
                    }
                    for (int i; (i = lvan.end.indexOf(removedLabel)) >= 0; ) {
                        lvan.start.remove(i);
                        lvan.end.remove(i);
                        lvan.index.remove(i);
                    }
                }
                return lvan.start.isEmpty();
            };

            if (methodNode.visibleLocalVariableAnnotations != null) {
                methodNode.visibleLocalVariableAnnotations.removeIf(filter);
                if (methodNode.visibleLocalVariableAnnotations.isEmpty()) {
                    methodNode.visibleLocalVariableAnnotations = null;
                }
            }
            if (methodNode.invisibleLocalVariableAnnotations != null) {
                methodNode.invisibleLocalVariableAnnotations.removeIf(filter);
                if (methodNode.invisibleLocalVariableAnnotations.isEmpty()) {
                    methodNode.invisibleLocalVariableAnnotations = null;
                }
            }
        }
    }

    //using raw types to prevent bridges
    private static final class ReachabilityInterpreter extends Interpreter {
        public static final ReachabilityInterpreter INSTANCE = new ReachabilityInterpreter(ASM5);

        public static final SourceValue VALUE = new SourceValue(1);

        private ReachabilityInterpreter(int api) {
            super(api);
        }

        @Override
        public Value newValue(Type type) {
            return VALUE;
        }

        @Override
        public Value newOperation(AbstractInsnNode insn) {
            return VALUE;
        }

        @Override
        public Value copyOperation(AbstractInsnNode insn, Value value) {
            return VALUE;
        }

        @Override
        public Value unaryOperation(AbstractInsnNode insn, Value value) {
            return VALUE;
        }

        @Override
        public Value binaryOperation(AbstractInsnNode insn, Value value1, Value value2) {
            return VALUE;
        }

        @Override
        public Value ternaryOperation(AbstractInsnNode insn, Value value1, Value value2, Value value3) {
            return VALUE;
        }

        @Override
        public Value naryOperation(AbstractInsnNode insn, List values) {
            return VALUE;
        }

        @Override
        public void returnOperation(AbstractInsnNode insn, Value value, Value expected) {
            //no-op
        }

        @Override
        public Value merge(Value value, Value w) {
            return value;
        }
    }

    //using raw types to prevent bridges
    private static final class ReachabilityFrame extends Frame {
        public static final ReachabilityFrame INSTANCE = new ReachabilityFrame();

        public ReachabilityFrame() {
            super(0, 0);
        }

        @Override
        public void setReturn(Value value) {
            //no-op
        }

        @Override
        public Value getLocal(int i) throws IndexOutOfBoundsException {
            return ReachabilityInterpreter.VALUE;
        }

        @Override
        public void setLocal(int i, Value value) throws IndexOutOfBoundsException {
            //no-op
        }

        @Override
        public Value pop() throws IndexOutOfBoundsException {
            return ReachabilityInterpreter.VALUE;
        }

        @Override
        public void push(Value value) throws IndexOutOfBoundsException {
            //no-op
        }
    }
}
