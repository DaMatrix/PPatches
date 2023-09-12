package net.daporkchop.ppatches.util.asm;

import com.google.common.base.Preconditions;
import lombok.SneakyThrows;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.InsnList;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;
import java.util.function.Consumer;

/**
 * An {@link InsnList} with added safety checks when inserting/removing instructions to/from a list.
 *
 * @author DaPorkchop_
 */
public class CheckedInsnList extends InsnList implements Iterable<AbstractInsnNode> {
    private static final MethodHandle INSN_INDEX_GETTER;

    static {
        try {
            Field field = AbstractInsnNode.class.getDeclaredField("index");
            field.setAccessible(true);
            INSN_INDEX_GETTER = MethodHandles.publicLookup().unreflectGetter(field);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    @SneakyThrows
    protected static boolean belongsToAnyList(AbstractInsnNode insn) {
        return insn.getPrevious() != null || insn.getNext() != null || (int) INSN_INDEX_GETTER.invokeExact(insn) >= 0;
    }
    
    protected final void checkNotInList(AbstractInsnNode insn) {
        Preconditions.checkArgument(!belongsToAnyList(insn), "instruction already belongs to a list: %s", insn);
    }

    protected final void checkDifferentList(InsnList insns) {
        Preconditions.checkArgument(this != insns, "given list is the same as this list: %s", this);
    }
    
    protected final void checkContains(AbstractInsnNode insn) {
        Preconditions.checkArgument(this.contains(insn), "instruction not found: %s", insn);
    }

    @Override
    public void set(AbstractInsnNode location, AbstractInsnNode insn) {
        this.checkContains(location);
        this.checkNotInList(insn);
        super.set(location, insn);
    }

    @Override
    public void add(AbstractInsnNode insn) {
        this.checkNotInList(insn);
        super.add(insn);
    }

    @Override
    public void add(InsnList insns) {
        this.checkDifferentList(insns);
        super.add(insns);
    }

    @Override
    public void insert(AbstractInsnNode insn) {
        this.checkNotInList(insn);
        super.insert(insn);
    }

    @Override
    public void insert(InsnList insns) {
        this.checkDifferentList(insns);
        super.insert(insns);
    }

    @Override
    public void insert(AbstractInsnNode location, AbstractInsnNode insn) {
        this.checkContains(location);
        this.checkNotInList(insn);
        super.insert(location, insn);
    }

    @Override
    public void insert(AbstractInsnNode location, InsnList insns) {
        this.checkContains(location);
        this.checkDifferentList(insns);
        super.insert(location, insns);
    }

    @Override
    public void insertBefore(AbstractInsnNode location, AbstractInsnNode insn) {
        this.checkContains(location);
        this.checkNotInList(insn);
        super.insertBefore(location, insn);
    }

    @Override
    public void insertBefore(AbstractInsnNode location, InsnList insns) {
        this.checkContains(location);
        this.checkDifferentList(insns);
        super.insertBefore(location, insns);
    }

    @Override
    public void remove(AbstractInsnNode insn) {
        this.checkContains(insn);
        super.remove(insn);
    }

    @Override
    public void forEach(Consumer<? super AbstractInsnNode> action) {
        for (AbstractInsnNode insn = this.getFirst(); insn != null; insn = insn.getNext()) {
            action.accept(insn);
        }
    }
}
