package net.daporkchop.ppatches.util.asm.analysis;

import com.google.common.base.Preconditions;
import net.daporkchop.ppatches.util.asm.TypeUtils;
import net.daporkchop.ppatches.util.asm.BytecodeHelper;
import net.daporkchop.ppatches.util.asm.CheckedInsnList;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.*;

import java.util.*;
import java.util.stream.Stream;

import static org.objectweb.asm.Opcodes.*;

/**
 * @author DaPorkchop_
 */
public final class AnalyzedInsnList extends CheckedInsnList implements AutoCloseable {
    private static final LabelNode NULL_SOURCE = new LabelNode();

    private static final SourceInterpreter SOURCE_INTERPRETER = new SourceInterpreter() {
        @Override
        public SourceValue newValue(Type type) {
            //use a dummy source instruction to indicate that the value comes from an unknown source
            return type == Type.VOID_TYPE ? null : new SourceValue(type.getSize(), NULL_SOURCE);
        }

        @Override
        public SourceValue copyOperation(AbstractInsnNode insn, SourceValue value) {
            //determine the size of loaded/storeed values without having to access the source value
            switch (insn.getOpcode()) {
                case ILOAD:
                case FLOAD:
                case ALOAD:
                case ISTORE:
                case FSTORE:
                case ASTORE:
                    return new SourceValue(1, insn);
                case LLOAD:
                case DLOAD:
                case LSTORE:
                case DSTORE:
                    return new SourceValue(2, insn);
            }
            return super.copyOperation(insn, value);
        }
    };

    protected final String ownerName;
    protected final MethodNode methodNode;

    protected final IdentityHashMap<LabelNode, Set<AbstractInsnNode>> incomingJumps = new IdentityHashMap<>();
    protected final IdentityHashMap<AbstractInsnNode, DynamicSourceFrame> dynamicFrames;

    public AnalyzedInsnList(String ownerName, MethodNode methodNode) {
        this.ownerName = ownerName;
        this.methodNode = methodNode;

        if (!methodNode.tryCatchBlocks.isEmpty()) { //TODO: deal with try-catch blocks
            throw new UnsupportedOperationException("try-catch blocks are not supported!");
        }

        //move all instructions from the MethodNode's instruction list to this list
        super.add(methodNode.instructions);
        methodNode.instructions = this;

        this.dynamicFrames = new IdentityHashMap<>(this.size());
        this.recompute();
    }

    private void recompute() {
        this.incomingJumps.clear();
        this.dynamicFrames.clear();

        if (!this.methodNode.tryCatchBlocks.isEmpty()) { //TODO: deal with try-catch blocks
            throw new UnsupportedOperationException("try-catch blocks are not supported!");
        }

        //ensure the method starts with a label
        if (super.getFirst() != null && !(super.getFirst() instanceof LabelNode)) {
            super.insert(new LabelNode());
        }

        //initialize local state for each instruction
        for (AbstractInsnNode insn = super.getFirst(); insn != null; insn = insn.getNext()) {
            this.createState(insn);
        }

        //track jumps to each label
        for (AbstractInsnNode insn = super.getFirst(); insn != null; insn = insn.getNext()) {
            this.trackOutgoingJumps(insn);
        }
    }

    private void createState(AbstractInsnNode insn) {
        //create DynamicSourceFrames for each instruction
        this.dynamicFrames.put(insn, new DynamicSourceFrame(insn));

        //create empty incoming instruction set for each label
        if (insn instanceof LabelNode) {
            this.incomingJumps.put((LabelNode) insn, BytecodeHelper.makeInsnSet());
        }
    }

    private void deleteState(AbstractInsnNode insn) {
        //create DynamicSourceFrames for each instruction
        this.dynamicFrames.remove(insn);

        //create empty incoming instruction set for each label
        if (insn instanceof LabelNode) {
            this.incomingJumps.remove((LabelNode) insn);
        }
    }

    private void trackOutgoingJumps(AbstractInsnNode insn) {
        if (BytecodeHelper.canAdvanceJumpingToLabel(insn)) {
            for (LabelNode possibleNextLabel : BytecodeHelper.possibleNextLabels(insn)) {
                this.incomingJumps.get(possibleNextLabel).add(insn);
            }
        }
    }

    private void untrackOutgoingJumps(AbstractInsnNode insn) {
        if (BytecodeHelper.canAdvanceJumpingToLabel(insn)) {
            for (LabelNode possibleNextLabel : BytecodeHelper.possibleNextLabels(insn)) {
                this.incomingJumps.get(possibleNextLabel).remove(insn);
            }
        }
    }

    @Override
    public void close() {
        this.incomingJumps.clear();
        this.dynamicFrames.clear();

        //move all instructions back to a simple InsnList
        InsnList simpleList = BytecodeHelper.makeInsnList();
        simpleList.add(this);
        this.methodNode.instructions = simpleList;
    }

    @Override
    public boolean contains(AbstractInsnNode insn) {
        return this.dynamicFrames.containsKey(insn);
    }

    @Override
    public void remove(AbstractInsnNode insn) {
        super.remove(insn);
        this.dynamicFrames.remove(insn);
    }

    private Stream<AbstractInsnNode> incomingInsns(LabelNode insn) {
        Stream<AbstractInsnNode> stream = this.incomingJumps.get(insn).stream();

        AbstractInsnNode prev = insn.getPrevious();
        if (prev != null && BytecodeHelper.canAdvanceNormallyToNextInstruction(prev)) {
            stream = Stream.concat(Stream.of(prev), stream);
        }

        return stream;
    }

    public boolean isUnreachable(AbstractInsnNode insn) {
        return false; //TODO
    }

    public SourceValue localSources(AbstractInsnNode insn, int localIndex, Type localType) {
        DynamicSourceFrame dynamicFrame = this.dynamicFrames.get(insn);
        Preconditions.checkArgument(dynamicFrame != null, "instruction not found: %s", insn);
        return dynamicFrame.getLocal(localIndex, localType);
    }

    public List<SourceValue> stackSources(AbstractInsnNode insn, Type... stackTypes) {
        DynamicSourceFrame dynamicFrame = this.dynamicFrames.get(insn);
        Preconditions.checkArgument(dynamicFrame != null, "instruction not found: %s", insn);

        List<SourceValue> sources = new ArrayList<>(stackTypes.length);
        for (int indexFromTop = -1, i = stackTypes.length - 1; i >= 0; i--) {
            Type sourceType = stackTypes[i];
            indexFromTop += sourceType.getSize();
            sources.set(i, dynamicFrame.getFromTopOfStack(indexFromTop, sourceType));
        }
        return sources;
    }

    public AbstractInsnNode singleStackSourceAtTop(AbstractInsnNode insn, Type stackType) {
        Preconditions.checkArgument(!(insn instanceof LabelNode), "instruction is a label: %s", insn);
        insn = insn.getPrevious();

        DynamicSourceFrame dynamicFrame = this.dynamicFrames.get(insn);
        Preconditions.checkArgument(dynamicFrame != null, "instruction not found: %s", insn);

        Set<AbstractInsnNode> sources = dynamicFrame.getFromTopOfStack(stackType.getSize() - 1, stackType).insns;
        return sources.size() == 1 ? sources.iterator().next() : null;
    }

    public ChangeBatch beginChanges() {
        return new ChangeBatch();
    }

    public class ChangeBatch implements AutoCloseable {
        private final IdentityHashMap<AbstractInsnNode, InsnList> insertedInsns = new IdentityHashMap<>();
        private final Set<AbstractInsnNode> removedInsns = BytecodeHelper.makeInsnSet();

        private void checkNotPendingRemoval(AbstractInsnNode insn) {
            AnalyzedInsnList.super.checkContains(insn);
            Preconditions.checkState(!this.removedInsns.contains(insn), "instruction already pending removal: %s", insn);
        }

        private InsnList findInsertionBuffer(AbstractInsnNode location) {
            if (location != null) { //location could be null for instructions being inserted at the head of the method
                AnalyzedInsnList.super.checkContains(location);
            }
            return this.insertedInsns.computeIfAbsent(location, unused -> BytecodeHelper.makeInsnList());
        }

        public void insertBefore(AbstractInsnNode location, AbstractInsnNode insn) {
            this.checkNotPendingRemoval(location);
            this.findInsertionBuffer(location.getPrevious()).add(insn);
        }

        public void insertBefore(AbstractInsnNode location, InsnList insns) {
            this.checkNotPendingRemoval(location);
            this.findInsertionBuffer(location.getPrevious()).add(insns);
        }

        public void insertAfter(AbstractInsnNode location, AbstractInsnNode insn) {
            this.checkNotPendingRemoval(location);
            this.findInsertionBuffer(location).insert(insn);
        }

        public void insertAfter(AbstractInsnNode location, InsnList insns) {
            this.checkNotPendingRemoval(location);
            this.findInsertionBuffer(location).insert(insns);
        }

        public void set(AbstractInsnNode location, AbstractInsnNode insn) {
            this.insertAfter(location, insn);
            this.remove(location);
        }

        public void remove(AbstractInsnNode insn) {
            this.checkNotPendingRemoval(insn);
            this.removedInsns.add(insn);
        }

        public void rollback() {
            this.insertedInsns.clear();
            this.removedInsns.clear();
        }

        @Override
        public void close() {
            //forget outgoing jump information for all instructions being removed
            this.removedInsns.forEach(AnalyzedInsnList.this::untrackOutgoingJumps);

            //insert the requested instructions and build a single ArrayList containing all of the inserted instructions
            List<AbstractInsnNode> allInsertedInsns = new ArrayList<>();
            this.insertedInsns.forEach((location, insns) -> {
                if (location == null && !(insns.getFirst() instanceof LabelNode)) { //if we're inserting at the beginning of the method, we'll ensure that the method starts with a label
                    insns.insert(new LabelNode());
                }

                allInsertedInsns.addAll(Arrays.asList(insns.toArray()));

                if (location == null) {
                    AnalyzedInsnList.super.insert(insns);
                } else {
                    AnalyzedInsnList.super.insert(location, insns);
                }
            });

            //remove all instructions which were pending removal
            this.removedInsns.forEach(AnalyzedInsnList.super::remove);

            //delete all state for all instructions being removed (we do this after removing them because otherwise assertions will fail to detect that the removed instruction is still
            // in this list)
            this.removedInsns.forEach(AnalyzedInsnList.this::deleteState);

            //initialize a new blank state for each instruction being inserted
            allInsertedInsns.forEach(AnalyzedInsnList.this::createState);

            //begin tracking outgoing jumps for each instruction being inserted
            allInsertedInsns.forEach(AnalyzedInsnList.this::trackOutgoingJumps);

            //reset this instance
            this.rollback();
        }
    }

    private static final int SOURCE_LOC_MASK = 0xFFFF;

    private static final int SOURCE_FLAG_STACK = 1 << 16;
    private static final int SOURCE_FLAG_LOCAL = 0;
    private static final int SOURCE_MASK = SOURCE_FLAG_STACK | SOURCE_FLAG_LOCAL;

    private static final int VALUE_FLAG_COPIED = 1 << 18;
    private static final int VALUE_FLAG_PASSEDTHROUGH = 1 << 19;
    private static final int VALUE_MASK = VALUE_FLAG_COPIED | VALUE_FLAG_PASSEDTHROUGH;

    private static final int FLAGS_STACK_COPIED = SOURCE_FLAG_STACK | VALUE_FLAG_COPIED;
    private static final int FLAGS_LOCAL_COPIED = SOURCE_FLAG_LOCAL | VALUE_FLAG_COPIED;
    private static final int FLAGS_STACK_PASSEDTHROUGH = SOURCE_FLAG_STACK | VALUE_FLAG_PASSEDTHROUGH;

    private static final int[] PUSHED_STACK_OPS_NOTHING = new int[0];
    private static final int[] PUSHED_STACK_OPS_NEW_SINGLE = new int[1];
    private static final int[] PUSHED_STACK_OPS_NEW_DOUBLE = new int[2];

    private class DynamicSourceFrame {
        private final AbstractInsnNode insn;

        private final int poppedStackOperandCount;
        private final int[] pushedStackOperands;

        private final int readFromLocalBase;
        private final int readFromLocalCount;

        private final int storedToLocalBase;
        private final int[] storedToLocalValues;

        private final boolean visible;

        @SuppressWarnings("PointlessBitwiseExpression")
        public DynamicSourceFrame(AbstractInsnNode insn) {
            this.insn = insn;

            int poppedStackOperandCount = 0;
            int[] pushedStackOperands = PUSHED_STACK_OPS_NOTHING;

            int readFromLocalBase = -1;
            int readFromLocalCount = -1;

            int storedToLocalBase = -1;
            int[] storedToLocalValues = null;

            boolean visible = true;

            final int opcode = insn.getOpcode();
            switch (opcode) {
                case -1:
                    assert insn instanceof LabelNode || insn instanceof FrameNode || insn instanceof LineNumberNode : BytecodeHelper.toString(insn);
                    //fallthrough
                case NOP:
                    break;
                case ACONST_NULL:
                case ICONST_M1:
                case ICONST_0:
                case ICONST_1:
                case ICONST_2:
                case ICONST_3:
                case ICONST_4:
                case ICONST_5:
                case FCONST_0:
                case FCONST_1:
                case FCONST_2:
                case BIPUSH:
                case SIPUSH:
                    pushedStackOperands = PUSHED_STACK_OPS_NEW_SINGLE;
                    break;
                case LCONST_0:
                case LCONST_1:
                case DCONST_0:
                case DCONST_1:
                    pushedStackOperands = PUSHED_STACK_OPS_NEW_DOUBLE;
                    break;
                case LDC: {
                    Object cst = ((LdcInsnNode) insn).cst;
                    pushedStackOperands = cst instanceof Long || cst instanceof Double ? PUSHED_STACK_OPS_NEW_DOUBLE : PUSHED_STACK_OPS_NEW_SINGLE;
                    break;
                }
                case ILOAD:
                case FLOAD:
                case ALOAD: {
                    int var = ((VarInsnNode) insn).var;
                    readFromLocalBase = var;
                    readFromLocalCount = 1;
                    pushedStackOperands = new int[]{FLAGS_LOCAL_COPIED | var};
                    break;
                }
                case LLOAD:
                case DLOAD: {
                    int var = ((VarInsnNode) insn).var;
                    readFromLocalBase = var;
                    readFromLocalCount = 2;
                    pushedStackOperands = new int[]{FLAGS_LOCAL_COPIED | var, FLAGS_LOCAL_COPIED | (var + 1)};
                    break;
                }
                case ISTORE:
                case FSTORE:
                case ASTORE:
                    poppedStackOperandCount = 1;
                    storedToLocalBase = ((VarInsnNode) insn).var;
                    storedToLocalValues = new int[]{FLAGS_STACK_COPIED | 0 };
                    break;
                case LSTORE:
                case DSTORE:
                    poppedStackOperandCount = 2;
                    storedToLocalBase = ((VarInsnNode) insn).var;
                    storedToLocalValues = new int[]{FLAGS_STACK_COPIED | 0, FLAGS_STACK_COPIED | 1 };
                    break;
                case IALOAD:
                case FALOAD:
                case AALOAD:
                case BALOAD:
                case CALOAD:
                case SALOAD:
                    poppedStackOperandCount = 2;
                    pushedStackOperands = PUSHED_STACK_OPS_NEW_SINGLE;
                    break;
                case LALOAD:
                case DALOAD:
                    poppedStackOperandCount = 2;
                    pushedStackOperands = PUSHED_STACK_OPS_NEW_DOUBLE;
                    break;
                case IASTORE:
                case FASTORE:
                case AASTORE:
                case BASTORE:
                case CASTORE:
                case SASTORE:
                    poppedStackOperandCount = 3;
                    break;
                case LASTORE:
                case DASTORE:
                    poppedStackOperandCount = 4;
                    break;
                case POP:
                    poppedStackOperandCount = 1;
                    visible = false;
                    break;
                case POP2:
                    poppedStackOperandCount = 2;
                    visible = false;
                    break;
                case DUP: //TODO: decide how to handle these
                    poppedStackOperandCount = 1;
                    pushedStackOperands = new int[]{FLAGS_STACK_COPIED | 0, FLAGS_STACK_COPIED | 0 };
                    break;
                case DUP_X1:
                    poppedStackOperandCount = 2;
                    pushedStackOperands = new int[]{FLAGS_STACK_COPIED | 1, FLAGS_STACK_PASSEDTHROUGH | 0, FLAGS_STACK_PASSEDTHROUGH | 1 };
                    break;
                case DUP_X2:
                    poppedStackOperandCount = 3;
                    pushedStackOperands = new int[]{FLAGS_STACK_COPIED | 2, FLAGS_STACK_PASSEDTHROUGH | 0, FLAGS_STACK_PASSEDTHROUGH | 1, FLAGS_STACK_PASSEDTHROUGH | 2 };
                    break;
                case DUP2:
                    poppedStackOperandCount = 2;
                    pushedStackOperands = new int[]{FLAGS_STACK_COPIED | 0, FLAGS_STACK_COPIED | 1, FLAGS_STACK_PASSEDTHROUGH | 0, FLAGS_STACK_PASSEDTHROUGH | 1 };
                    break;
                case DUP2_X1:
                    poppedStackOperandCount = 3;
                    pushedStackOperands = new int[]{FLAGS_STACK_COPIED | 1, FLAGS_STACK_COPIED | 2, FLAGS_STACK_PASSEDTHROUGH | 0, FLAGS_STACK_PASSEDTHROUGH | 1, FLAGS_STACK_PASSEDTHROUGH | 2 };
                    break;
                case DUP2_X2:
                    poppedStackOperandCount = 4;
                    pushedStackOperands = new int[]{FLAGS_STACK_COPIED | 2, FLAGS_STACK_COPIED | 3, FLAGS_STACK_PASSEDTHROUGH | 0, FLAGS_STACK_PASSEDTHROUGH | 1, FLAGS_STACK_PASSEDTHROUGH | 2, FLAGS_STACK_PASSEDTHROUGH | 3 };
                    break;
                case SWAP: //TODO: decide if i want this to be visible
                    poppedStackOperandCount = 2;
                    pushedStackOperands = new int[]{FLAGS_STACK_PASSEDTHROUGH | 1, FLAGS_STACK_PASSEDTHROUGH | 0 };
                    break;
                case IADD:
                case FADD:
                case ISUB:
                case FSUB:
                case IMUL:
                case FMUL:
                case IDIV:
                case FDIV:
                case IREM:
                case FREM:
                    poppedStackOperandCount = 2;
                    pushedStackOperands = PUSHED_STACK_OPS_NEW_SINGLE;
                    break;
                case LADD:
                case DADD:
                case LSUB:
                case DSUB:
                case LMUL:
                case DMUL:
                case LDIV:
                case DDIV:
                case LREM:
                case DREM:
                    poppedStackOperandCount = 4;
                    pushedStackOperands = PUSHED_STACK_OPS_NEW_DOUBLE;
                    break;
                case INEG:
                case FNEG:
                    poppedStackOperandCount = 1;
                    pushedStackOperands = PUSHED_STACK_OPS_NEW_SINGLE;
                    break;
                case LNEG:
                case DNEG:
                    poppedStackOperandCount = 2;
                    pushedStackOperands = PUSHED_STACK_OPS_NEW_DOUBLE;
                    break;
                case ISHL:
                case ISHR:
                case IUSHR:
                case IAND:
                case IOR:
                case IXOR:
                    poppedStackOperandCount = 2;
                    pushedStackOperands = PUSHED_STACK_OPS_NEW_SINGLE;
                    break;
                case LSHL:
                case LSHR:
                case LUSHR:
                    poppedStackOperandCount = 3; //pops a long (2 slots) and an int (1 slot)
                    pushedStackOperands = PUSHED_STACK_OPS_NEW_DOUBLE;
                    break;
                case LAND:
                case LOR:
                case LXOR:
                    poppedStackOperandCount = 4;
                    pushedStackOperands = PUSHED_STACK_OPS_NEW_DOUBLE;
                    break;
                case IINC: {
                    IincInsnNode iincInsn = (IincInsnNode) insn;
                    readFromLocalBase = iincInsn.var;
                    readFromLocalCount = 1;
                    storedToLocalBase = iincInsn.var;
                    storedToLocalValues = PUSHED_STACK_OPS_NEW_SINGLE;
                    break;
                }
                case I2F:
                case F2I:
                case I2B:
                case I2C:
                case I2S:
                    poppedStackOperandCount = 1;
                    pushedStackOperands = PUSHED_STACK_OPS_NEW_SINGLE;
                    break;
                case I2L:
                case I2D:
                case F2L:
                case F2D:
                    poppedStackOperandCount = 1;
                    pushedStackOperands = PUSHED_STACK_OPS_NEW_DOUBLE;
                    break;
                case L2I:
                case L2F:
                case D2I:
                case D2F:
                    poppedStackOperandCount = 2;
                    pushedStackOperands = PUSHED_STACK_OPS_NEW_SINGLE;
                    break;
                case L2D:
                case D2L:
                    poppedStackOperandCount = 2;
                    pushedStackOperands = PUSHED_STACK_OPS_NEW_DOUBLE;
                    break;
                case FCMPL:
                case FCMPG:
                    poppedStackOperandCount = 2;
                    pushedStackOperands = PUSHED_STACK_OPS_NEW_SINGLE;
                    break;
                case LCMP:
                case DCMPL:
                case DCMPG:
                    poppedStackOperandCount = 4;
                    pushedStackOperands = PUSHED_STACK_OPS_NEW_SINGLE;
                    break;
                case IFEQ:
                case IFNE:
                case IFLT:
                case IFGE:
                case IFGT:
                case IFLE:
                    poppedStackOperandCount = 1;
                    break;
                case IF_ICMPEQ:
                case IF_ICMPNE:
                case IF_ICMPLT:
                case IF_ICMPGE:
                case IF_ICMPGT:
                case IF_ICMPLE:
                case IF_ACMPEQ:
                case IF_ACMPNE:
                    poppedStackOperandCount = 2;
                    break;
                case GOTO:
                    break;
                case TABLESWITCH:
                case LOOKUPSWITCH:
                    poppedStackOperandCount = 1;
                    break;
                case IRETURN:
                case FRETURN:
                case ARETURN:
                    poppedStackOperandCount = 1;
                    break;
                case LRETURN:
                case DRETURN:
                    poppedStackOperandCount = 2;
                    break;
                case RETURN:
                    break;
                case GETSTATIC:
                case PUTSTATIC:
                case GETFIELD:
                case PUTFIELD: {
                    int typeSize = TypeUtils.getTypeSize(((FieldInsnNode) insn).desc);
                    switch (opcode) {
                        case GETFIELD:
                            poppedStackOperandCount = 1;
                            //fallthrough
                        case GETSTATIC:
                            pushedStackOperands = typeSize == 2 ? PUSHED_STACK_OPS_NEW_DOUBLE : PUSHED_STACK_OPS_NEW_SINGLE;
                            break;
                        case PUTSTATIC:
                            poppedStackOperandCount = typeSize;
                            break;
                        case PUTFIELD:
                            poppedStackOperandCount = 1 + typeSize;
                            break;
                    }
                    break;
                }
                case INVOKEVIRTUAL:
                case INVOKESPECIAL:
                case INVOKESTATIC:
                case INVOKEINTERFACE: {
                    int argumentsAndReturnSizes = Type.getArgumentsAndReturnSizes(((MethodInsnNode) insn).desc);
                    int argumentsSizes = TypeUtils.extractArgumentsSizes(argumentsAndReturnSizes);
                    poppedStackOperandCount = opcode == INVOKESTATIC ? argumentsSizes : argumentsSizes + 1;
                    pushedStackOperands = TypeUtils.extractReturnSize(argumentsAndReturnSizes) == 2 ? PUSHED_STACK_OPS_NEW_DOUBLE : PUSHED_STACK_OPS_NEW_SINGLE;
                    break;
                }
                case INVOKEDYNAMIC: {
                    int argumentsAndReturnSizes = Type.getArgumentsAndReturnSizes(((InvokeDynamicInsnNode) insn).desc);
                    poppedStackOperandCount = TypeUtils.extractArgumentsSizes(argumentsAndReturnSizes);
                    pushedStackOperands = TypeUtils.extractReturnSize(argumentsAndReturnSizes) == 2 ? PUSHED_STACK_OPS_NEW_DOUBLE : PUSHED_STACK_OPS_NEW_SINGLE;
                    break;
                }
                case NEW:
                    pushedStackOperands = PUSHED_STACK_OPS_NEW_SINGLE;
                    break;
                case NEWARRAY:
                case ANEWARRAY:
                case ARRAYLENGTH:
                    poppedStackOperandCount = 1;
                    pushedStackOperands = PUSHED_STACK_OPS_NEW_SINGLE;
                    break;
                case ATHROW:
                    poppedStackOperandCount = 1;
                    break;
                case CHECKCAST:
                case INSTANCEOF:
                    poppedStackOperandCount = 1;
                    pushedStackOperands = PUSHED_STACK_OPS_NEW_SINGLE;
                    break;
                case MONITORENTER:
                case MONITOREXIT:
                    poppedStackOperandCount = 1;
                    break;
                case MULTIANEWARRAY:
                    poppedStackOperandCount = ((MultiANewArrayInsnNode) insn).dims;
                    pushedStackOperands = PUSHED_STACK_OPS_NEW_SINGLE;
                    break;
                case IFNULL:
                case IFNONNULL:
                    poppedStackOperandCount = 1;
                    break;

                case JSR:
                case RET: //TODO: do we want to support these?
                default:
                    throw new IllegalArgumentException(BytecodeHelper.toString(insn));
            }
            
            this.poppedStackOperandCount = poppedStackOperandCount;
            this.pushedStackOperands = pushedStackOperands;

            this.readFromLocalBase = readFromLocalBase;
            this.readFromLocalCount = readFromLocalCount;

            this.storedToLocalBase = storedToLocalBase;
            this.storedToLocalValues = storedToLocalValues;

            this.visible = visible;

            assert (readFromLocalBase < 0) == (readFromLocalCount < 0) : "readFromLocalBase and readFromLocalCount must be either both set or both unset";
            assert (storedToLocalBase < 0) == (storedToLocalValues == null) : "storedToLocalBase and storedToLocalValues must be either both set or both unset";

            assert Arrays.stream(pushedStackOperands)
                    .allMatch(pushedOperandFlags -> pushedOperandFlags == 0
                                                    || ((pushedOperandFlags & SOURCE_MASK) == SOURCE_FLAG_LOCAL && (pushedOperandFlags & VALUE_MASK) == VALUE_FLAG_COPIED)
                                                    || ((pushedOperandFlags & SOURCE_MASK) == SOURCE_FLAG_STACK && (pushedOperandFlags & SOURCE_LOC_MASK) < this.poppedStackOperandCount))
                    : "a pushed stack operand has incorrect flags?!?";

            assert storedToLocalValues == null || Arrays.stream(storedToLocalValues).allMatch(storedValueFlags -> storedValueFlags == 0 || (storedValueFlags & VALUE_MASK) == VALUE_FLAG_COPIED)
                    : "a stored local value wasn't created from scratch or copied?!?";
        }

        private DynamicSourceFrame getPrevious() {
            AbstractInsnNode prev = this.insn.getPrevious();
            if (prev == null) {
                //TODO: something
                throw new IllegalStateException();
            }
            assert BytecodeHelper.canAdvanceNormallyToNextInstruction(prev) : "previous instruction can't advance normally to this instruction?!?";
            return AnalyzedInsnList.this.dynamicFrames.get(this.insn.getPrevious());
        }

        public SourceValue getLocal(int i, Type type) {
            if (this.storedToLocalBase >= 0 && i >= this.storedToLocalBase && i < this.storedToLocalBase + this.storedToLocalValues.length) {
                //this instruction wrote to the local variable in question, so it's the only source which should be visible beyond this point
                return SOURCE_INTERPRETER.copyOperation(this.insn, null);
            }

            if (this.insn instanceof LabelNode) { //we've reached the start of the current block, so we want to recurse into the incoming instructions
                if (this.insn.getPrevious() == null) { //this is the method start label, so any local variable sources must be method arguments
                    MethodNode methodNode = AnalyzedInsnList.this.methodNode;
                    int argumentSizes = (BytecodeHelper.isStatic(methodNode) ? 0 : 1) + TypeUtils.extractArgumentsSizes(Type.getArgumentsAndReturnSizes(methodNode.desc));
                    Preconditions.checkElementIndex(i, argumentSizes);

                    return SOURCE_INTERPRETER.newValue(type);
                }

                //TODO: i think we need to do a BFS/DFS here, because loops would probably make this keep recursing forever
                return AnalyzedInsnList.this.incomingInsns((LabelNode) this.insn)
                        .map(incomingInsn -> AnalyzedInsnList.this.dynamicFrames.get(incomingInsn).getLocal(i, type))
                        .reduce(SOURCE_INTERPRETER::merge).get();
            }

            return this.getPrevious().getLocal(i, type);
        }

        public SourceValue getFromTopOfStack(int indexFromTop, Type type) {
            return this.getFromTopOfStack(indexFromTop, type.getSize());
        }

        public SourceValue getFromTopOfStack(int indexFromTop, int typeSize) {
            return this.getFromTopOfStack0(indexFromTop, typeSize);
        }

        private SourceValue getFromTopOfStack0(int indexFromTop, int typeSize) {
            Preconditions.checkArgument(typeSize == 1 || typeSize == 2, "invalid type size", typeSize);

            if (indexFromTop >= 0 && indexFromTop < this.pushedStackOperands.length) {
                //this instruction pushed the stack element in question, so it's the only source which should be visible beyond this point

                int indexFromThis = this.pushedStackOperands.length - 1 - indexFromTop; //the index into this.pushedStackOperands
                Preconditions.checkPositionIndexes(indexFromThis, indexFromThis + typeSize, this.pushedStackOperands.length);

                int pushedOperandFlags = this.pushedStackOperands[indexFromThis];
                int upperPushedOperandFlags = typeSize == 2 ? this.pushedStackOperands[indexFromThis + 1] : 0;
                if (pushedOperandFlags == 0) { //the stack element in question was produced entirely by this instruction
                    Preconditions.checkState(typeSize == 1 || upperPushedOperandFlags == 0, "upper operand flags mismatched", pushedOperandFlags, upperPushedOperandFlags);

                    return new SourceValue(typeSize, this.insn);
                }

                //the stack element in question was copied or passed through from the stack or a local variable
                Preconditions.checkState(typeSize == 1 || upperPushedOperandFlags == ((pushedOperandFlags & ~SOURCE_LOC_MASK) | ((pushedOperandFlags & SOURCE_LOC_MASK) + 1)),
                        "upper operand flags mismatched", pushedOperandFlags, upperPushedOperandFlags);

                if ((pushedOperandFlags & SOURCE_MASK) == SOURCE_FLAG_STACK) {
                    if ((pushedOperandFlags & VALUE_MASK) == VALUE_FLAG_COPIED) { //the stack element in question was copied from one of the popped stack elements
                        return new SourceValue(typeSize, this.insn);
                    } else if ((pushedOperandFlags & VALUE_MASK) == VALUE_FLAG_PASSEDTHROUGH) { //the stack element in question was passed through from one of the popped stack elements
                        //track down the original value from its original source
                        int originalIndexFromThis = pushedOperandFlags & SOURCE_LOC_MASK;
                        int originalIndexFromTop = this.poppedStackOperandCount - 1 - originalIndexFromThis;
                        return this.getPrevious().getFromTopOfStack(originalIndexFromTop, typeSize);
                    } else {
                        throw new AssertionError("pushedOperandFlags & VALUE_MASK is " + (pushedOperandFlags & VALUE_MASK)); //unreachable
                    }
                } else if ((pushedOperandFlags & SOURCE_MASK) == SOURCE_FLAG_LOCAL) { //the stack element in question was copied from a local variable
                    assert (pushedOperandFlags & VALUE_MASK) == VALUE_FLAG_COPIED;

                    return new SourceValue(typeSize, this.insn);
                } else {
                    throw new AssertionError(); //unreachable
                }
            }

            if (this.insn instanceof LabelNode) { //merge the results from all the incoming instructions
                //TODO: i think we need to do a BFS/DFS here, because loops would probably make this keep recursing forever
                return AnalyzedInsnList.this.incomingInsns((LabelNode) this.insn)
                        .map(incomingInsn -> AnalyzedInsnList.this.dynamicFrames.get(incomingInsn).getFromTopOfStack0(indexFromTop, typeSize))
                        .reduce(SOURCE_INTERPRETER::merge).get();
            }

            return this.getPrevious().getFromTopOfStack0(indexFromTop + this.poppedStackOperandCount - this.pushedStackOperands.length, typeSize);
        }
    }
}
