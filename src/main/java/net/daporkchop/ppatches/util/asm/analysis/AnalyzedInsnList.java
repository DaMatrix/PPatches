package net.daporkchop.ppatches.util.asm.analysis;

import com.google.common.base.Preconditions;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import net.daporkchop.ppatches.util.COWArrayUtils;
import net.daporkchop.ppatches.util.asm.TypeUtils;
import net.daporkchop.ppatches.util.asm.BytecodeHelper;
import net.daporkchop.ppatches.util.asm.CheckedInsnList;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.*;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.objectweb.asm.Opcodes.*;

/**
 * @author DaPorkchop_
 */
public final class AnalyzedInsnList extends CheckedInsnList implements IDataflowProvider, AutoCloseable {
    private static final LabelNode NULL_SOURCE = new LabelNode();

    private static final LabelNode ARGUMENT_SOURCE = new LabelNode();
    private static final LabelNode EXCEPTION_SOURCE = new LabelNode();

    private static final SourceValue[] ARGUMENT_SOURCE_VALUE_BY_SIZE = {null, new SourceValue(1, ARGUMENT_SOURCE), new SourceValue(2, ARGUMENT_SOURCE)};
    private static final SourceValue EXCEPTION_SOURCE_VALUE = new SourceValue(1, EXCEPTION_SOURCE);

    static final SourceInterpreter SOURCE_INTERPRETER = new SourceInterpreter() {
        @Override
        public SourceValue newValue(Type type) {
            //use a dummy source instruction to indicate that the value comes from an unknown source
            return type == Type.VOID_TYPE ? null : new SourceValue(type.getSize(), NULL_SOURCE);
        }

        @Override
        public SourceValue copyOperation(AbstractInsnNode insn, SourceValue value) {
            //determine the size of loaded/stored values without having to access the source value
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

    private static SourceValue mergeNullable(SourceValue a, SourceValue b) {
        return a != null ? (b != null ? SOURCE_INTERPRETER.merge(a, b) : a) : b;
    }

    protected final String ownerName;
    protected final MethodNode methodNode;

    protected final IdentityHashMap<LabelNode, Set<AbstractInsnNode>> incomingJumps = new IdentityHashMap<>();
    protected final IdentityHashMap<AbstractInsnNode, DynamicSourceFrame> dynamicFrames;

    protected final IdentityHashMap<LabelNode, TryCatchBlockNode> tryCatchBlocksByHandlers = new IdentityHashMap<>();

    public AnalyzedInsnList(String ownerName, MethodNode methodNode) {
        this.ownerName = ownerName;
        this.methodNode = methodNode;

        //move all instructions from the MethodNode's instruction list to this list
        super.add(methodNode.instructions);
        methodNode.instructions = this;

        this.dynamicFrames = new IdentityHashMap<>(this.size());
        this.recompute();
    }

    private void recompute() {
        this.incomingJumps.clear();
        this.dynamicFrames.clear();
        this.tryCatchBlocksByHandlers.clear();

        //ensure the method starts with a label
        if (super.getFirst() != null && !(super.getFirst() instanceof LabelNode)) {
            super.insert(new LabelNode());
        }

        //index all the try-catch block handlers
        for (TryCatchBlockNode tryCatchBlockNode : this.methodNode.tryCatchBlocks) {
            Preconditions.checkState(this.tryCatchBlocksByHandlers.put(tryCatchBlockNode.handler, tryCatchBlockNode) == null, "method contains multiple try-catch blocks with the same handler?!?");
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
        if (BytecodeHelper.canAdvanceNormallyToNextInstruction(insn) && insn.getNext() instanceof LabelNode) {
            Preconditions.checkState(!this.tryCatchBlocksByHandlers.containsKey((LabelNode) insn.getNext()), "instruction advances normally to a try-catch block handler?!?");
        }
        if (BytecodeHelper.canAdvanceJumpingToLabel(insn)) {
            for (LabelNode possibleNextLabel : BytecodeHelper.possibleNextLabels(insn)) {
                Preconditions.checkState(!this.tryCatchBlocksByHandlers.containsKey(possibleNextLabel), "instruction jumps to a try-catch block handler?!?");
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
        this.tryCatchBlocksByHandlers.clear();

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
    public void set(AbstractInsnNode location, AbstractInsnNode insn) {
        throw new UnsupportedOperationException("AnalyzedInsnList only allows changes through a ChangeBatch");
    }

    @Override
    public void add(AbstractInsnNode insn) {
        throw new UnsupportedOperationException("AnalyzedInsnList only allows changes through a ChangeBatch");
    }

    @Override
    public void add(InsnList insns) {
        throw new UnsupportedOperationException("AnalyzedInsnList only allows changes through a ChangeBatch");
    }

    @Override
    public void insert(AbstractInsnNode insn) {
        throw new UnsupportedOperationException("AnalyzedInsnList only allows changes through a ChangeBatch");
    }

    @Override
    public void insert(InsnList insns) {
        throw new UnsupportedOperationException("AnalyzedInsnList only allows changes through a ChangeBatch");
    }

    @Override
    public void insert(AbstractInsnNode location, AbstractInsnNode insn) {
        throw new UnsupportedOperationException("AnalyzedInsnList only allows changes through a ChangeBatch");
    }

    @Override
    public void insert(AbstractInsnNode location, InsnList insns) {
        throw new UnsupportedOperationException("AnalyzedInsnList only allows changes through a ChangeBatch");
    }

    @Override
    public void insertBefore(AbstractInsnNode location, AbstractInsnNode insn) {
        throw new UnsupportedOperationException("AnalyzedInsnList only allows changes through a ChangeBatch");
    }

    @Override
    public void insertBefore(AbstractInsnNode location, InsnList insns) {
        throw new UnsupportedOperationException("AnalyzedInsnList only allows changes through a ChangeBatch");
    }

    @Override
    public void remove(AbstractInsnNode insn) {
        throw new UnsupportedOperationException("AnalyzedInsnList only allows changes through a ChangeBatch");
    }

    @Override
    public void clear() {
        throw new UnsupportedOperationException("AnalyzedInsnList only allows changes through a ChangeBatch");
    }

    private Stream<AbstractInsnNode> incomingInsns(LabelNode insn) {
        Stream.Builder<AbstractInsnNode> builder = Stream.builder();

        for (AbstractInsnNode incomingJump : this.incomingJumps.get(insn)) {
            builder.add(incomingJump);
        }

        AbstractInsnNode prev = insn.getPrevious();
        if (prev != null && BytecodeHelper.canAdvanceNormallyToNextInstruction(prev)) {
            builder.add(prev);
        }

        return builder.build();
    }

    private void incomingInsns(DynamicSourceFrame frame, LabelNode insn, ArrayDeque<DynamicSourceFrame> dfsStack) {
        for (AbstractInsnNode incomingJump : this.incomingJumps.get(insn)) {
            dfsStack.push(AnalyzedInsnList.this.getDynamicFrame(incomingJump));
        }

        AbstractInsnNode prev = insn.getPrevious();
        if (prev != null && BytecodeHelper.canAdvanceNormallyToNextInstruction(prev)) {
            dfsStack.push(AnalyzedInsnList.this.getDynamicFrame(prev));
        }
    }

    private void incomingInsnsDFS(DynamicSourceFrame frame, LabelNode insn, ArrayDeque<DFSItem> dfsStack, int indexBefore) {
        for (AbstractInsnNode incomingJump : this.incomingJumps.get(insn)) {
            DynamicSourceFrame incomingFrame = AnalyzedInsnList.this.getDynamicFrame(incomingJump);
            int incomingIndexFromThis = incomingFrame.convertIndexAfterToIndexFromThis(indexBefore);
            dfsStack.push(new DFSItem(incomingFrame, incomingIndexFromThis));
        }

        AbstractInsnNode prev = insn.getPrevious();
        if (prev != null && BytecodeHelper.canAdvanceNormallyToNextInstruction(prev)) {
            DynamicSourceFrame prevFrame = AnalyzedInsnList.this.getDynamicFrame(prev);
            int prevIndexFromThis = prevFrame.convertIndexAfterToIndexFromThis(indexBefore);
            dfsStack.push(new DFSItem(prevFrame, prevIndexFromThis));
        }
    }

    private Stream<AbstractInsnNode> tryBlockInsns(TryCatchBlockNode tryCatchBlock) {
        Stream.Builder<AbstractInsnNode> builder = Stream.builder();
        for (AbstractInsnNode insn = tryCatchBlock.start; insn != tryCatchBlock.end; insn = insn.getNext()) {
            builder.add(insn);
        }
        return builder.build();
    }

    private void tryBlockInsns(TryCatchBlockNode tryCatchBlock, ArrayDeque<DynamicSourceFrame> dfsStack) {
        for (AbstractInsnNode insn = tryCatchBlock.start; insn != tryCatchBlock.end; insn = insn.getNext()) {
            dfsStack.push(this.getDynamicFrame(insn));
        }
    }

    private static final LabelNode[] EMPTY_LABELNODE_ARRAY = {};

    private LabelNode[] getExceptionHandlers(AbstractInsnNode insn) {
        List<TryCatchBlockNode> tryCatchBlocks = this.methodNode.tryCatchBlocks;
        LabelNode[] labels = EMPTY_LABELNODE_ARRAY;
        if (!tryCatchBlocks.isEmpty()) {
            int insnIndex = super.indexOf(insn);
            for (TryCatchBlockNode tryCatchBlock : tryCatchBlocks) {
                if (super.indexOf(tryCatchBlock.start) <= insnIndex && insnIndex < super.indexOf(tryCatchBlock.end)) {
                    labels = COWArrayUtils.append(labels, tryCatchBlock.handler);
                }
            }
        }
        return labels;
    }

    private DynamicSourceFrame getDynamicFrame(AbstractInsnNode insn) {
        DynamicSourceFrame dynamicFrame = this.dynamicFrames.get(insn);
        Preconditions.checkArgument(dynamicFrame != null, "instruction not found: %s", insn);
        return dynamicFrame;
    }

    @Override
    public boolean isUnreachable(AbstractInsnNode insn) {
        return false; //TODO
    }

    @Override
    public List<SourceValue> getStackOperandSources(AbstractInsnNode insn) {
        DynamicSourceFrame dynamicFrame = this.getDynamicFrame(insn);

        List<SourceValue> sources = new ArrayList<>(dynamicFrame.poppedStackOperandCount);
        if (!(insn instanceof LabelNode)) {
            DynamicSourceFrame prevDynamicFrame = dynamicFrame.getPreviousInBlock();
            for (int indexFromCurr = 0; indexFromCurr < dynamicFrame.poppedStackOperandCount; ) {
                int indexBefore = dynamicFrame.convertIndexFromThisToIndexBefore(indexFromCurr);
                int prevIndexFromThis = prevDynamicFrame.convertIndexAfterToIndexFromThis(indexBefore);
                SourceValue sourceValue = prevDynamicFrame.getStackSources(prevIndexFromThis);

                indexFromCurr += sourceValue.size;
                sources.add(sourceValue);
            }
        }
        return sources;
    }

    @Override
    public SourceValue getLocalSources(AbstractInsnNode insn, int localIndex) {
        DynamicSourceFrame dynamicFrame = this.getDynamicFrame(insn);
        return (insn instanceof LabelNode ? dynamicFrame : dynamicFrame.getPreviousInBlock()).getLocalSources(localIndex);
    }

    @Override
    public List<UsageValue> getStackUsages(AbstractInsnNode insn) {
        return this.getDynamicFrame(insn).getStackUsages();
    }

    @Override
    public UsageValue getLocalUsages(AbstractInsnNode insn) {
        return this.getDynamicFrame(insn).getLocalUsages();
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
        
        private void checkNotTryCatchBlockLabel(AbstractInsnNode insn) {
            if (insn instanceof LabelNode) {
                for (TryCatchBlockNode tryCatchBlock : AnalyzedInsnList.this.methodNode.tryCatchBlocks) {
                    Preconditions.checkState(insn != tryCatchBlock.start, "tried to remove a try-catch block start label?!?", insn);
                    Preconditions.checkState(insn != tryCatchBlock.end, "tried to remove a try-catch block end label?!?", insn);
                    Preconditions.checkState(insn != tryCatchBlock.handler, "tried to remove a try-catch block handler label?!?", insn);
                }
            }
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
            this.checkNotTryCatchBlockLabel(insn);
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

    private static final int SOURCE_FLAG_NEW = 0;
    private static final int SOURCE_FLAG_STACK = 1 << 16;
    private static final int SOURCE_FLAG_LOCAL = 1 << 17;
    private static final int SOURCE_MASK = SOURCE_FLAG_STACK | SOURCE_FLAG_LOCAL;

    private static final int VALUE_FLAG_COPIED = 1 << 18;
    private static final int VALUE_FLAG_PASSEDTHROUGH = 1 << 19;
    private static final int VALUE_MASK = VALUE_FLAG_COPIED | VALUE_FLAG_PASSEDTHROUGH;

    private static final int FLAGS_STACK_COPIED = SOURCE_FLAG_STACK | VALUE_FLAG_COPIED;
    private static final int FLAGS_LOCAL_COPIED = SOURCE_FLAG_LOCAL | VALUE_FLAG_COPIED;
    private static final int FLAGS_STACK_PASSEDTHROUGH = SOURCE_FLAG_STACK | VALUE_FLAG_PASSEDTHROUGH;

    private static final int SIZE_SHIFT = 20;
    private static final int SIZE_MASK = 0x3;
    private static final int SIZE_VALUE_1 = 1;
    private static final int SIZE_VALUE_2 = 2;
    private static final int SIZE_VALUE_UNKNOWN = 3;

    private static int extractSize(int flags) {
        return (flags >>> SIZE_SHIFT) & SIZE_MASK;
    }

    private static final int FLAG_2ND = 1 << 22;

    private static final int[] PUSHED_STACK_OPS_NOTHING = {};
    private static final int[] PUSHED_STACK_OPS_NEW_SINGLE = {SOURCE_FLAG_NEW | (SIZE_VALUE_1 << SIZE_SHIFT)};
    private static final int[] PUSHED_STACK_OPS_NEW_DOUBLE = {SOURCE_FLAG_NEW | (SIZE_VALUE_2 << SIZE_SHIFT), SOURCE_FLAG_NEW | (SIZE_VALUE_2 << SIZE_SHIFT) | FLAG_2ND};
    private static final int[][] PUSHED_STACK_OPS_NEW_BY_SIZE = {PUSHED_STACK_OPS_NOTHING, PUSHED_STACK_OPS_NEW_SINGLE, PUSHED_STACK_OPS_NEW_DOUBLE};

    private static final int[] PUSHED_STACK_OPS_STORE_SINGLE = {
            FLAGS_STACK_COPIED | (SIZE_VALUE_1 << SIZE_SHIFT) | 0
    };
    private static final int[] PUSHED_STACK_OPS_STORE_DOUBLE = {
            FLAGS_STACK_COPIED | (SIZE_VALUE_2 << SIZE_SHIFT) | 0,
            FLAGS_STACK_COPIED | (SIZE_VALUE_2 << SIZE_SHIFT) | FLAG_2ND | 1
    };
    private static final int[] PUSHED_STACK_OPS_DUP = {
            FLAGS_STACK_PASSEDTHROUGH | (SIZE_VALUE_1 << SIZE_SHIFT) | 0,
            FLAGS_STACK_COPIED | (SIZE_VALUE_1 << SIZE_SHIFT) | 0
    };
    private static final int[] PUSHED_STACK_OPS_DUP_X1 = {
            FLAGS_STACK_COPIED | (SIZE_VALUE_1 << SIZE_SHIFT) | 1,
            FLAGS_STACK_PASSEDTHROUGH | (SIZE_VALUE_1 << SIZE_SHIFT) | 0,
            FLAGS_STACK_PASSEDTHROUGH | (SIZE_VALUE_1 << SIZE_SHIFT) | 1
    };
    private static final int[] PUSHED_STACK_OPS_DUP_X2 = {
            FLAGS_STACK_COPIED | (SIZE_VALUE_1 << SIZE_SHIFT) | 2,
            FLAGS_STACK_PASSEDTHROUGH | (SIZE_VALUE_UNKNOWN << SIZE_SHIFT) | 0,
            FLAGS_STACK_PASSEDTHROUGH | (SIZE_VALUE_UNKNOWN << SIZE_SHIFT) | 1,
            FLAGS_STACK_PASSEDTHROUGH | (SIZE_VALUE_1 << SIZE_SHIFT) | 2
    };
    private static final int[] PUSHED_STACK_OPS_DUP2 = {
            FLAGS_STACK_COPIED | (SIZE_VALUE_UNKNOWN << SIZE_SHIFT) | 0,
            FLAGS_STACK_COPIED | (SIZE_VALUE_UNKNOWN << SIZE_SHIFT) | 1,
            FLAGS_STACK_PASSEDTHROUGH | (SIZE_VALUE_UNKNOWN << SIZE_SHIFT) | 0,
            FLAGS_STACK_PASSEDTHROUGH | (SIZE_VALUE_UNKNOWN << SIZE_SHIFT) | 1
    };
    private static final int[] PUSHED_STACK_OPS_DUP2_X1 = {
            FLAGS_STACK_COPIED | (SIZE_VALUE_UNKNOWN << SIZE_SHIFT) | 1,
            FLAGS_STACK_COPIED | (SIZE_VALUE_UNKNOWN << SIZE_SHIFT) | 2,
            FLAGS_STACK_PASSEDTHROUGH | (SIZE_VALUE_1 << SIZE_SHIFT) | 0,
            FLAGS_STACK_PASSEDTHROUGH | (SIZE_VALUE_UNKNOWN << SIZE_SHIFT) | 1,
            FLAGS_STACK_PASSEDTHROUGH | (SIZE_VALUE_UNKNOWN << SIZE_SHIFT) | 2
    };
    private static final int[] PUSHED_STACK_OPS_DUP2_X2 = {
            FLAGS_STACK_COPIED | (SIZE_VALUE_UNKNOWN << SIZE_SHIFT) | 2,
            FLAGS_STACK_COPIED | (SIZE_VALUE_UNKNOWN << SIZE_SHIFT) | 3,
            FLAGS_STACK_PASSEDTHROUGH | (SIZE_VALUE_UNKNOWN << SIZE_SHIFT) | 0,
            FLAGS_STACK_PASSEDTHROUGH | (SIZE_VALUE_UNKNOWN << SIZE_SHIFT) | 1,
            FLAGS_STACK_PASSEDTHROUGH | (SIZE_VALUE_UNKNOWN << SIZE_SHIFT) | 2,
            FLAGS_STACK_PASSEDTHROUGH | (SIZE_VALUE_UNKNOWN << SIZE_SHIFT) | 3
    };
    private static final int[] PUSHED_STACK_OPS_SWAP = {
            FLAGS_STACK_PASSEDTHROUGH | (SIZE_VALUE_1 << SIZE_SHIFT) | 1,
            FLAGS_STACK_PASSEDTHROUGH | (SIZE_VALUE_1 << SIZE_SHIFT) | 0
    };

    private static int[] makePushedStackOpsLoadSingle(int var) {
        return new int[]{
                FLAGS_LOCAL_COPIED | (SIZE_VALUE_1 << SIZE_SHIFT) | var,
        };
    }

    private static int[] makePushedStackOpsLoadDouble(int var) {
        return new int[]{
                FLAGS_LOCAL_COPIED | (SIZE_VALUE_2 << SIZE_SHIFT) | var,
                FLAGS_LOCAL_COPIED | (SIZE_VALUE_2 << SIZE_SHIFT) | FLAG_2ND | (var + 1),
        };
    }
    
    private static final int[][] PUSHED_STACK_OPS_LOAD_CACHE_SINGLE;
    private static final int[][] PUSHED_STACK_OPS_LOAD_CACHE_DOUBLE;
    
    static {
        final int cacheSize = 40;

        PUSHED_STACK_OPS_LOAD_CACHE_SINGLE = new int[cacheSize][];
        for (int var = 0; var < cacheSize; var++) {
            PUSHED_STACK_OPS_LOAD_CACHE_SINGLE[var] = makePushedStackOpsLoadSingle(var);
        }

        PUSHED_STACK_OPS_LOAD_CACHE_DOUBLE = new int[cacheSize][];
        for (int var = 0; var < cacheSize; var++) {
            PUSHED_STACK_OPS_LOAD_CACHE_DOUBLE[var] = makePushedStackOpsLoadDouble(var);
        }
    }

    private static int[] PUSHED_STACK_OPS_LOAD_SINGLE(int var) {
        return var <= PUSHED_STACK_OPS_LOAD_CACHE_SINGLE.length
                ? PUSHED_STACK_OPS_LOAD_CACHE_SINGLE[var]
                : makePushedStackOpsLoadSingle(var);
    }

    private static int[] PUSHED_STACK_OPS_LOAD_DOUBLE(int var) {
        return var <= PUSHED_STACK_OPS_LOAD_CACHE_DOUBLE.length
                ? PUSHED_STACK_OPS_LOAD_CACHE_DOUBLE[var]
                : makePushedStackOpsLoadDouble(var);
    }

    private static boolean validateFlags(int flags) {
        switch (extractSize(flags)) {
            case SIZE_VALUE_UNKNOWN:
                Preconditions.checkArgument((flags & FLAG_2ND) == 0, "value is marked 2nd, but its size is unknown");
                break;
            case SIZE_VALUE_1:
                Preconditions.checkArgument((flags & FLAG_2ND) == 0, "value is marked 2nd, but its size is 1");
                break;
            case SIZE_VALUE_2:
                break;
            default:
                throw new IllegalArgumentException("bad size: " + extractSize(flags));
        }

        switch (flags & SOURCE_MASK) {
            case SOURCE_FLAG_NEW:
                Preconditions.checkArgument(extractSize(flags) != SIZE_VALUE_UNKNOWN, "value is marked new, but its size is unknown");
                break;
            case SOURCE_FLAG_STACK:
            case SOURCE_FLAG_LOCAL:
                Preconditions.checkArgument((flags & VALUE_MASK) == VALUE_FLAG_COPIED || (flags & VALUE_MASK) == VALUE_FLAG_PASSEDTHROUGH, "");
                break;
            default:
                throw new IllegalArgumentException("bad source: " + (flags & SOURCE_MASK));
        }

        switch (flags & VALUE_MASK) {
            case 0:
            case VALUE_FLAG_COPIED:
            case VALUE_FLAG_PASSEDTHROUGH:
                break;
            default:
                throw new IllegalArgumentException("bad value: " + (flags & VALUE_MASK));
        }

        return true;
    }

    private static String flagsToString(int flags) {
        StringBuilder builder = new StringBuilder();
        builder.append("source=");
        switch (flags & SOURCE_MASK) {
            case SOURCE_FLAG_NEW:
                builder.append("new");
                break;
            case SOURCE_FLAG_STACK:
                builder.append("stack[").append(flags & SOURCE_LOC_MASK).append(']');
                break;
            case SOURCE_FLAG_LOCAL:
                builder.append("local[").append(flags & SOURCE_LOC_MASK).append(']');
                break;
            default:
                throw new IllegalArgumentException("bad source: " + (flags & SOURCE_MASK));
        }

        switch (flags & VALUE_MASK) {
            case 0:
                break;
            case VALUE_FLAG_COPIED:
                builder.append(", copied");
                break;
            case VALUE_FLAG_PASSEDTHROUGH:
                builder.append(", passed_through");
                break;
            default:
                throw new IllegalArgumentException("bad value: " + (flags & VALUE_MASK));
        }

        builder.append(", size=");
        switch (extractSize(flags)) {
            case SIZE_VALUE_1:
            case SIZE_VALUE_2:
                builder.append(extractSize(flags));
                break;
            case SIZE_VALUE_UNKNOWN:
                builder.append("unknown");
                break;
            default:
                throw new IllegalArgumentException("bad size: " + extractSize(flags));
        }

        if ((flags & FLAG_2ND) != 0) {
            assert extractSize(flags) == SIZE_VALUE_2 : "value is marked 2nd, but its size is " + extractSize(flags);
            builder.append(" (2nd)");
        }

        return builder.toString();
    }

    private final class DynamicSourceFrame {
        private final AbstractInsnNode insn;

        private final int poppedStackOperandCount;
        private final int[] pushedStackOperands;

        private final int readFromLocalBase;
        private final int readFromLocalCount;

        private final int storedToLocalBase;
        private final int[] storedToLocalValues;

        private final boolean visible;

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
                    pushedStackOperands = PUSHED_STACK_OPS_LOAD_SINGLE(var);
                    break;
                }
                case LLOAD:
                case DLOAD: {
                    int var = ((VarInsnNode) insn).var;
                    readFromLocalBase = var;
                    readFromLocalCount = 2;
                    pushedStackOperands = PUSHED_STACK_OPS_LOAD_DOUBLE(var);
                    break;
                }
                case ISTORE:
                case FSTORE:
                case ASTORE:
                    poppedStackOperandCount = 1;
                    storedToLocalBase = ((VarInsnNode) insn).var;
                    storedToLocalValues = PUSHED_STACK_OPS_STORE_SINGLE;
                    break;
                case LSTORE:
                case DSTORE:
                    poppedStackOperandCount = 2;
                    storedToLocalBase = ((VarInsnNode) insn).var;
                    storedToLocalValues = PUSHED_STACK_OPS_STORE_DOUBLE;
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
                case DUP: //value -> value, value
                    poppedStackOperandCount = 1;
                    pushedStackOperands = PUSHED_STACK_OPS_DUP;
                    break;
                case DUP_X1: //value2, value1 -> value1, value2, value1
                    poppedStackOperandCount = 2;
                    pushedStackOperands = PUSHED_STACK_OPS_DUP_X1;
                    break;
                case DUP_X2: //value3, value2, value1 -> value1, value3, value2, value1
                    poppedStackOperandCount = 3;
                    pushedStackOperands = PUSHED_STACK_OPS_DUP_X2;
                    break;
                case DUP2:
                    poppedStackOperandCount = 2;
                    pushedStackOperands = PUSHED_STACK_OPS_DUP2;
                    break;
                case DUP2_X1:
                    poppedStackOperandCount = 3;
                    pushedStackOperands = PUSHED_STACK_OPS_DUP2_X1;
                    break;
                case DUP2_X2:
                    poppedStackOperandCount = 4;
                    pushedStackOperands = PUSHED_STACK_OPS_DUP2_X2;
                    break;
                case SWAP: //TODO: decide if i want this to be visible
                    poppedStackOperandCount = 2;
                    pushedStackOperands = PUSHED_STACK_OPS_SWAP;
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
                            pushedStackOperands = PUSHED_STACK_OPS_NEW_BY_SIZE[typeSize];
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
                    pushedStackOperands = PUSHED_STACK_OPS_NEW_BY_SIZE[TypeUtils.extractReturnSize(argumentsAndReturnSizes)];
                    break;
                }
                case INVOKEDYNAMIC: {
                    int argumentsAndReturnSizes = Type.getArgumentsAndReturnSizes(((InvokeDynamicInsnNode) insn).desc);
                    poppedStackOperandCount = TypeUtils.extractArgumentsSizes(argumentsAndReturnSizes);
                    pushedStackOperands = PUSHED_STACK_OPS_NEW_BY_SIZE[TypeUtils.extractReturnSize(argumentsAndReturnSizes)];
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

            assert (readFromLocalBase < 0) == (readFromLocalCount < 0) : "readFromLocalBase and readFromLocalCount must be either both set or both unset " + BytecodeHelper.toString(insn);
            assert (storedToLocalBase < 0) == (storedToLocalValues == null) : "storedToLocalBase and storedToLocalValues must be either both set or both unset " + BytecodeHelper.toString(insn);

            assert Arrays.stream(pushedStackOperands).allMatch(AnalyzedInsnList::validateFlags) : BytecodeHelper.toString(insn);
            assert Arrays.stream(pushedStackOperands)
                    .allMatch(pushedOperandFlags -> (pushedOperandFlags & SOURCE_MASK) == SOURCE_FLAG_NEW
                               || ((pushedOperandFlags & SOURCE_MASK) == SOURCE_FLAG_LOCAL && (pushedOperandFlags & VALUE_MASK) == VALUE_FLAG_COPIED && extractSize(pushedOperandFlags) != SIZE_VALUE_UNKNOWN)
                               || ((pushedOperandFlags & SOURCE_MASK) == SOURCE_FLAG_STACK && (pushedOperandFlags & SOURCE_LOC_MASK) < this.poppedStackOperandCount))
                    : "a pushed stack operand has incorrect flags?!? " + BytecodeHelper.toString(insn);

            assert storedToLocalValues == null || Arrays.stream(storedToLocalValues).allMatch(AnalyzedInsnList::validateFlags) : BytecodeHelper.toString(insn);
            assert storedToLocalValues == null || Arrays.stream(storedToLocalValues)
                    .allMatch(storedValueFlags -> ((storedValueFlags & SOURCE_MASK) == SOURCE_FLAG_NEW || (storedValueFlags & VALUE_MASK) == VALUE_FLAG_COPIED)
                                                  && extractSize(storedValueFlags) == this.storedToLocalValues.length)
                    : "a stored local value has incorrect flags?!? " + BytecodeHelper.toString(insn);
        }

        private DynamicSourceFrame getPreviousInBlock() {
            Preconditions.checkState(!(this.insn instanceof LabelNode), "getPreviousInBlock called on a label", this.insn);
            return this.getPreviousTotal();
        }

        private DynamicSourceFrame getPreviousTotal() {
            AbstractInsnNode prev = this.insn.getPrevious();
            assert BytecodeHelper.canAdvanceNormallyToNextInstruction(prev) : "previous instruction can't advance normally to this instruction?!?";
            return AnalyzedInsnList.this.getDynamicFrame(prev);
        }

        private int convertIndexFromThisToIndexBefore(int indexFromThis) {
            return indexFromThis - this.poppedStackOperandCount;
        }

        private int convertIndexFromThisToIndexAfter(int indexFromThis) {
            return indexFromThis - this.pushedStackOperands.length;
        }

        private int convertIndexBeforeToIndexFromThis(int indexBefore) {
            return indexBefore + this.poppedStackOperandCount;
        }

        private int convertIndexAfterToIndexFromThis(int indexAfter) {
            return indexAfter + this.pushedStackOperands.length;
        }

        public SourceValue getLocalSources(int localIndex) {
            ArrayDeque<DynamicSourceFrame> dfsStack = new ArrayDeque<>();
            Set<AbstractInsnNode> visited = BytecodeHelper.makeInsnSet();

            SourceValue result = null;

            DFS_LOOP:
            for (DynamicSourceFrame currFrame = this; currFrame != null; currFrame = dfsStack.pollFirst()) {
                if (!visited.add(currFrame.insn)) { //we've already visited this instruction, advance to the next one
                    continue;
                }

                if (currFrame.storedToLocalBase >= 0 && localIndex >= currFrame.storedToLocalBase && localIndex < currFrame.storedToLocalBase + currFrame.storedToLocalValues.length) {
                    //this instruction wrote to the local variable in question, so it's the only source which should be visible beyond this point
                    int storedValueFlags = currFrame.storedToLocalValues[localIndex - currFrame.storedToLocalBase];

                    Preconditions.checkState((storedValueFlags & FLAG_2ND) == 0, "attempted to read from the 2nd part of a category 2 type");
                    assert extractSize(storedValueFlags) == currFrame.storedToLocalValues.length : "flags report a different value size than the stored values array";

                    if ((storedValueFlags & SOURCE_MASK) == SOURCE_FLAG_NEW || (storedValueFlags & VALUE_MASK) == VALUE_FLAG_COPIED) {
                        SourceValue nextResult = new SourceValue(extractSize(storedValueFlags), currFrame.insn);
                        result = mergeNullable(result, nextResult); //tail return: "return nextResult;"
                        continue;
                    } else {
                        throw new AssertionError(); //unreachable
                    }
                }

                if (currFrame.insn instanceof LabelNode) {
                    TryCatchBlockNode tryCatchBlock = AnalyzedInsnList.this.tryCatchBlocksByHandlers.get(currFrame.insn);
                    if (tryCatchBlock != null) { //this label is a try-catch block handler
                        //we want to merge all of the possible sources which the local variable may have, which means we'll have to merge the sources for every
                        // instruction in the try block.

                        AnalyzedInsnList.this.tryBlockInsns(tryCatchBlock, dfsStack);
                        continue;
                    }

                    if (currFrame.insn.getPrevious() == null) { //this is the method start label, so any local variable sources must be method arguments
                        MethodNode methodNode = AnalyzedInsnList.this.methodNode;

                        int currentLocalIndex = 0;
                        if (!BytecodeHelper.isStatic(methodNode)) {
                            currentLocalIndex++;
                            if (localIndex == 0) {
                                result = mergeNullable(result, ARGUMENT_SOURCE_VALUE_BY_SIZE[1]); //tail return: "return ARGUMENT_SOURCE_VALUE_BY_SIZE[1];"
                                continue;
                            }
                        }

                        for (Type argumentType : Type.getArgumentTypes(methodNode.desc)) {
                            int argumentSize = argumentType.getSize();
                            if (localIndex >= currentLocalIndex && localIndex < currentLocalIndex + argumentSize) {
                                result = mergeNullable(result, ARGUMENT_SOURCE_VALUE_BY_SIZE[argumentSize]); //tail return: "return ARGUMENT_SOURCE_VALUE_BY_SIZE[argumentSize];"
                                continue DFS_LOOP;
                            }
                            currentLocalIndex += argumentSize;
                        }

                        throw new IndexOutOfBoundsException("invalid method argument LVT index for " + methodNode.desc + ": " + localIndex);
                    }

                    //we've reached the start of the current block, so we want to recurse into the incoming instructions
                    AnalyzedInsnList.this.incomingInsns(currFrame, (LabelNode) currFrame.insn, dfsStack);
                    continue;
                }

                dfsStack.push(this.getPreviousInBlock()); //tail recursion: "return currFrame.getPrevious().getLocalSources(localIndex);"
                continue;
            }

            visited.clear();
            return Objects.requireNonNull(result, "result");
        }

        public SourceValue getStackSources(int _indexFromThis) {
            ArrayDeque<DFSItem> dfsStack = new ArrayDeque<>();
            Set<AbstractInsnNode> visited = BytecodeHelper.makeInsnSet();

            SourceValue result = null;

            DynamicSourceFrame currFrame = this;
            int indexFromThis = _indexFromThis;
            while (true) {
                if (currFrame == null || !visited.add(currFrame.insn)) { //we've already visited this instruction, advance to the next one
                    DFSItem nextItem = dfsStack.pollFirst();
                    if (nextItem == null) { //we've run out of instructions to visit, return the final result
                        visited.clear();
                        return Objects.requireNonNull(result, "result");
                    }
                    currFrame = nextItem.frame;
                    indexFromThis = nextItem.indexFromFrame;
                    nextItem.frame = null;
                    continue;
                }

                if (indexFromThis >= 0 && indexFromThis < currFrame.pushedStackOperands.length) {
                    //this instruction pushed the stack element in question, so it's the only source which should be visible beyond this point
                    int pushedOperandFlags = currFrame.pushedStackOperands[indexFromThis];

                    Preconditions.checkState((pushedOperandFlags & FLAG_2ND) == 0, "attempted to read from the 2nd part of a category 2 type");

                    switch ((pushedOperandFlags & SOURCE_MASK)) {
                        case SOURCE_FLAG_STACK:
                            if ((pushedOperandFlags & VALUE_MASK) == VALUE_FLAG_PASSEDTHROUGH) {
                                //the value is being passed through, recurse into the original value
                                DynamicSourceFrame prev = currFrame.getPreviousInBlock();
                                int originalIndexFromThis = pushedOperandFlags & SOURCE_LOC_MASK;
                                int indexBefore = currFrame.convertIndexFromThisToIndexBefore(originalIndexFromThis);
                                int prevIndexFromThis = prev.convertIndexAfterToIndexFromThis(indexBefore);

                                //tail recursion: "return prev.getStackSources(prevIndexFromThis);"
                                currFrame = prev;
                                indexFromThis = prevIndexFromThis;
                                continue;
                            } else if (extractSize(pushedOperandFlags) == SIZE_VALUE_UNKNOWN) {
                                break;
                            }
                            //fallthrough
                        case SOURCE_FLAG_LOCAL:
                        case SOURCE_FLAG_NEW:
                            assert extractSize(pushedOperandFlags) != SIZE_VALUE_UNKNOWN;

                            SourceValue nextResult = new SourceValue(extractSize(pushedOperandFlags), currFrame.insn);
                            result = mergeNullable(result, nextResult); //tail return: "return nextResult;"
                            currFrame = null; //set currFrame to null to forcibly advance to the next DFS item
                            continue;
                        default:
                            throw new AssertionError(); //unreachable
                    }

                    assert extractSize(pushedOperandFlags) == SIZE_VALUE_UNKNOWN;

                    //resolve the stack operand's size
                    SourceValue nextResult = new SourceValue(currFrame.getStackSourceSize(indexFromThis), currFrame.insn); //TODO: go directly to the previous frame once i figure out the index conversion
                    result = mergeNullable(result, nextResult); //tail return: "return nextResult;"
                    currFrame = null; //set currFrame to null to forcibly advance to the next DFS item
                    continue;
                }

                if (currFrame.insn instanceof LabelNode) {
                    TryCatchBlockNode tryCatchBlock = AnalyzedInsnList.this.tryCatchBlocksByHandlers.get(currFrame.insn);
                    if (tryCatchBlock != null) { //this label is a try-catch block handler
                        if (indexFromThis == -1) { //the caught exception is being queried
                            result = mergeNullable(result, EXCEPTION_SOURCE_VALUE); //tail return: "return EXCEPTION_SOURCE_VALUE;"
                            currFrame = null; //set currFrame to null to forcibly advance to the next DFS item
                            continue;
                        } else {
                            throw new IndexOutOfBoundsException("try-catch block handler only has a single stack operand!");
                        }
                    }

                    //merge the results from all the incoming instructions
                    int indexBefore = currFrame.convertIndexFromThisToIndexBefore(indexFromThis);
                    AnalyzedInsnList.this.incomingInsnsDFS(currFrame, (LabelNode) currFrame.insn, dfsStack, indexBefore);

                    currFrame = null; //set currFrame to null to forcibly advance to the next DFS item
                    continue;
                }

                DynamicSourceFrame prev = currFrame.getPreviousInBlock();
                int indexBefore = currFrame.convertIndexFromThisToIndexBefore(indexFromThis);
                int prevIndexFromThis = prev.convertIndexAfterToIndexFromThis(indexBefore);

                //tail recursion: "return prev.getStackSources(prevIndexFromThis);"
                currFrame = prev;
                indexFromThis = prevIndexFromThis;
                continue;
            }
        }

        public int getStackSourceSize(int indexFromThis) {
            //most important part of getStackSourceSize0 is hoisted out here, since it's the most likely part to be reached
            if (indexFromThis >= 0 && indexFromThis < this.pushedStackOperands.length) {
                //this instruction pushed the stack element in question, so it's the only source which should be visible beyond this point
                int pushedOperandFlags = this.pushedStackOperands[indexFromThis];

                Preconditions.checkState((pushedOperandFlags & FLAG_2ND) == 0, "attempted to read from the 2nd part of a category 2 type");

                if (extractSize(pushedOperandFlags) != SIZE_VALUE_UNKNOWN) { //the value's size is known, we can return it
                    return extractSize(pushedOperandFlags);
                }
            }

            return this.getStackSourceSize0(indexFromThis);
        }

        private int getStackSourceSize0(int _indexFromThis) {
            ArrayDeque<DFSItem> dfsStack = new ArrayDeque<>();
            Set<AbstractInsnNode> visited = BytecodeHelper.makeInsnSet();

            DynamicSourceFrame currFrame = this;
            int indexFromThis = _indexFromThis;
            while (true) {
                if (!visited.add(currFrame.insn)) { //we've already visited this instruction, advance to the next one
                    DFSItem nextItem = dfsStack.pop();
                    currFrame = nextItem.frame;
                    indexFromThis = nextItem.indexFromFrame;
                    nextItem.frame = null;
                    continue;
                }

                if (indexFromThis >= 0 && indexFromThis < currFrame.pushedStackOperands.length) {
                    //this instruction pushed the stack element in question, so it's the only source which should be visible beyond this point
                    int pushedOperandFlags = currFrame.pushedStackOperands[indexFromThis];

                    Preconditions.checkState((pushedOperandFlags & FLAG_2ND) == 0, "attempted to read from the 2nd part of a category 2 type");

                    if (extractSize(pushedOperandFlags) != SIZE_VALUE_UNKNOWN) { //the value's size is known, we can return it
                        visited.clear();
                        return extractSize(pushedOperandFlags);
                    }

                    Preconditions.checkState((pushedOperandFlags & SOURCE_MASK) == SOURCE_FLAG_STACK && ((pushedOperandFlags & VALUE_MASK) == VALUE_FLAG_COPIED || (pushedOperandFlags & VALUE_MASK) == VALUE_FLAG_PASSEDTHROUGH),
                            "pushed stack operand has unknown size, but is not the result of a stack copy or passthrough");

                    DynamicSourceFrame prev = currFrame.getPreviousInBlock();
                    int originalIndexFromThis = pushedOperandFlags & SOURCE_LOC_MASK;
                    int originalIndexBefore = currFrame.convertIndexFromThisToIndexBefore(originalIndexFromThis);
                    int prevOriginalIndexFromThis = prev.convertIndexAfterToIndexFromThis(originalIndexBefore);

                    //tail recursion: "return prev.getStackSourceSize0(prevOriginalIndexFromThis);"
                    currFrame = prev;
                    indexFromThis = prevOriginalIndexFromThis;
                    continue;
                }

                if (currFrame.insn instanceof LabelNode) {
                    TryCatchBlockNode tryCatchBlock = AnalyzedInsnList.this.tryCatchBlocksByHandlers.get(currFrame.insn);
                    if (tryCatchBlock != null) { //this label is a try-catch block handler
                        if (indexFromThis == -1) { //the caught exception is being queried
                            visited.clear();
                            return 1;
                        } else {
                            throw new IndexOutOfBoundsException("try-catch block handler only has a single stack operand!");
                        }
                    }

                    //find the first result from one of the the incoming instructions
                    int indexBefore = currFrame.convertIndexFromThisToIndexBefore(indexFromThis);
                    AnalyzedInsnList.this.incomingInsnsDFS(currFrame, (LabelNode) currFrame.insn, dfsStack, indexBefore);

                    //tail recursion into the first of the incoming instructions we just found (other instructions will be visited on subsequent iterations)
                    DFSItem nextItem = dfsStack.pop();
                    currFrame = nextItem.frame;
                    indexFromThis = nextItem.indexFromFrame;
                    nextItem.frame = null;
                    continue;
                }

                DynamicSourceFrame prev = currFrame.getPreviousInBlock();
                int indexBefore = currFrame.convertIndexFromThisToIndexBefore(indexFromThis);
                int prevIndexFromThis = prev.convertIndexAfterToIndexFromThis(indexBefore);

                //tail recursion: "return prev.getStackSourceSize0(prevIndexFromThis);"
                currFrame = prev;
                indexFromThis = prevIndexFromThis;
                continue;
            }
        }

        private void getNextFrames(boolean visitExceptionHandlers, ArrayDeque<DynamicSourceFrame> dfsStack) {
            if (BytecodeHelper.canAdvanceNormallyToNextInstruction(this.insn)) {
                dfsStack.push(AnalyzedInsnList.this.getDynamicFrame(this.insn.getNext()));
            }
            if (BytecodeHelper.canAdvanceJumpingToLabel(this.insn)) {
                for (LabelNode nextLabel : BytecodeHelper.possibleNextLabels(this.insn)) {
                    dfsStack.push(AnalyzedInsnList.this.getDynamicFrame(nextLabel));
                }
            }
            if (visitExceptionHandlers) {
                for (LabelNode exceptionHandler : AnalyzedInsnList.this.getExceptionHandlers(this.insn)) {
                    dfsStack.push(AnalyzedInsnList.this.getDynamicFrame(exceptionHandler));
                }
            }
        }

        private void getNextFramesDFS(boolean visitExceptionHandlers, ArrayDeque<DFSItem> dfsStack, int indexAfter) {
            if (BytecodeHelper.canAdvanceNormallyToNextInstruction(this.insn)) {
                DynamicSourceFrame nextFrame = AnalyzedInsnList.this.getDynamicFrame(this.insn.getNext());
                int nextIndexFromThis = nextFrame.convertIndexBeforeToIndexFromThis(indexAfter);
                dfsStack.push(new DFSItem(nextFrame, nextIndexFromThis));
            }
            if (BytecodeHelper.canAdvanceJumpingToLabel(this.insn)) {
                for (LabelNode nextLabel : BytecodeHelper.possibleNextLabels(this.insn)) {
                    DynamicSourceFrame nextFrame = AnalyzedInsnList.this.getDynamicFrame(nextLabel);
                    int nextIndexFromThis = nextFrame.convertIndexBeforeToIndexFromThis(indexAfter);
                    dfsStack.push(new DFSItem(nextFrame, nextIndexFromThis));
                }
            }
            if (visitExceptionHandlers) {
                for (LabelNode exceptionHandler : AnalyzedInsnList.this.getExceptionHandlers(this.insn)) {
                    DynamicSourceFrame nextFrame = AnalyzedInsnList.this.getDynamicFrame(exceptionHandler);
                    int nextIndexFromThis = nextFrame.convertIndexBeforeToIndexFromThis(indexAfter);
                    dfsStack.push(new DFSItem(nextFrame, nextIndexFromThis));
                }
            }
        }

        public UsageValue getLocalUsages() {
            int localIndex = this.storedToLocalBase;
            if (localIndex < 0) { //this instruction doesn't store any local variables
                return null;
            }

            ArrayDeque<DynamicSourceFrame> dfsStack = new ArrayDeque<>();
            Set<AbstractInsnNode> visited = BytecodeHelper.makeInsnSet();

            this.getNextFrames(true, dfsStack);

            Set<AbstractInsnNode> usages = BytecodeHelper.makeInsnSet();

            for (DynamicSourceFrame currFrame; (currFrame = dfsStack.pollFirst()) != null; ) {
                if (!visited.add(currFrame.insn)) { //we've already visited this instruction, advance to the next one
                    continue;
                }

                if (currFrame.readFromLocalBase >= 0 && localIndex >= currFrame.readFromLocalBase && localIndex < currFrame.readFromLocalBase + currFrame.readFromLocalCount) {
                    //this instruction reads the given local variable
                    usages.add(currFrame.insn);
                }
                if (currFrame.storedToLocalBase >= 0 && localIndex >= currFrame.storedToLocalBase && localIndex < currFrame.storedToLocalBase + currFrame.storedToLocalValues.length) {
                    //this instruction writes to the given local variable, so we can stop recursing
                    continue;
                }

                //advance to all of the next instructions
                currFrame.getNextFrames(true, dfsStack);
            }

            //we've run out of instructions to visit, return the final result
            visited.clear();

            return new UsageValue(this.storedToLocalValues.length, usages);
        }

        public List<UsageValue> getStackUsages() {
            if (this.pushedStackOperands.length == 0) { //this instruction doesn't push anything onto the stack
                return Collections.emptyList();
            }

            List<UsageValue> usages = new ArrayList<>(this.pushedStackOperands.length);
            for (int indexFromThis = 0; indexFromThis < this.pushedStackOperands.length; ) {
                if ((this.pushedStackOperands[indexFromThis] & VALUE_MASK) == VALUE_FLAG_PASSEDTHROUGH) {
                    //the pushed value is being passed through this instruction, we won't consider it to be an actual usage
                    indexFromThis++;
                    continue;
                }

                int indexAfter = this.convertIndexFromThisToIndexAfter(indexFromThis);

                //compute the size of the pushed value
                int valueSize = this.getStackSourceSize(indexFromThis);

                indexFromThis += valueSize;
                usages.add(this.getStackUsages0(indexAfter, valueSize));
            }
            return usages;
        }

        private UsageValue getStackUsages0(int _indexAfter, int valueSize) {
            ArrayDeque<DFSItem> dfsStack = new ArrayDeque<>();
            Set<AbstractInsnNode> visited = BytecodeHelper.makeInsnSet();

            //we don't want to visit exception handlers, as the stack will be cleared if an exception is thrown (we assume exception handlers are otherwise unreachable)
            this.getNextFramesDFS(false, dfsStack, _indexAfter);

            UsageValue result = null;

            DynamicSourceFrame currFrame = null;
            int indexFromThis = 0;
            while (true) {
                if (currFrame == null || !visited.add(currFrame.insn)) { //we've already visited this instruction, advance to the next one
                    DFSItem nextItem = dfsStack.pollFirst();
                    if (nextItem == null) { //we've run out of instructions to visit, return the final result
                        visited.clear();
                        return result != null ? result : new UsageValue(valueSize);
                    }
                    currFrame = nextItem.frame;
                    indexFromThis = nextItem.indexFromFrame;
                    nextItem.frame = null;
                    continue;
                }

                if (indexFromThis >= 0 && indexFromThis < currFrame.poppedStackOperandCount) {
                    //this instruction popped the stack element in question, so it's the only source which should be visible beyond this point

                    if (currFrame.visible) {
                        result = UsageValue.mergeNullable(result, new UsageValue(valueSize, currFrame.insn));
                    }

                    for (int pushedIndexFromThis = 0; pushedIndexFromThis < currFrame.pushedStackOperands.length; pushedIndexFromThis++) {
                        int pushedOperandFlags = currFrame.pushedStackOperands[pushedIndexFromThis];
                        if ((pushedOperandFlags & VALUE_MASK) == VALUE_FLAG_PASSEDTHROUGH && (pushedOperandFlags & SOURCE_LOC_MASK) == indexFromThis) {
                            //the value is being transparently passed through to the next instruction, we should include the usages of the passed-down value as well

                            assert (pushedOperandFlags & FLAG_2ND) == 0;

                            int indexAfter = currFrame.convertIndexFromThisToIndexAfter(pushedIndexFromThis);
                            //we don't want to visit exception handlers, as the stack will be cleared if an exception is thrown (we assume exception handlers are otherwise unreachable)
                            currFrame.getNextFramesDFS(false, dfsStack, indexAfter);
                            break;
                        }
                    }

                    currFrame = null; //set currFrame to null to forcibly advance to the next DFS item
                    continue;
                }

                int indexAfter = currFrame.convertIndexFromThisToIndexAfter(indexFromThis);
                //we don't want to visit exception handlers, as the stack will be cleared if an exception is thrown (we assume exception handlers are otherwise unreachable)
                currFrame.getNextFramesDFS(false, dfsStack, indexAfter);

                currFrame = null; //set currFrame to null to forcibly advance to the next DFS item
                continue;
            }
        }

        @Override
        public String toString() {
            return "popped=" + this.poppedStackOperandCount
                   + " pushed=" + Arrays.stream(this.pushedStackOperands).mapToObj(AnalyzedInsnList::flagsToString).collect(Collectors.joining(", ", "[", "]"))
                   + " visible=" + this.visible;
        }
    }

    @AllArgsConstructor
    @NoArgsConstructor
    private static final class DFSItem {
        public DynamicSourceFrame frame;
        public int indexFromFrame;
    }
}
