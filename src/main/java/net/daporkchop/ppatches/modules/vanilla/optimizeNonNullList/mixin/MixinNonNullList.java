package net.daporkchop.ppatches.modules.vanilla.optimizeNonNullList.mixin;

import net.minecraft.util.NonNullList;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.AbstractList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Spliterator;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

/**
 * @author DaPorkchop_
 */
@Mixin(NonNullList.class)
abstract class MixinNonNullList<E> extends AbstractList<E> {
    @Shadow
    @Final
    private List<E> delegate;

    /*@ModifyArg(method = { "withSize", "from" },
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/util/NonNullList;<init>(Ljava/util/List;Ljava/lang/Object;)V"),
            allow = 2, require = 2)
    private static List<?> ppatches_optimizeNonNullList_forciblyConvertToArrayList(List<?> list) {
        return new ArrayList<>(list);
    }*/

    @Override
    public boolean isEmpty() {
        return this.delegate.isEmpty();
    }

    @Override
    public boolean contains(Object o) {
        return o != null && this.delegate.contains(o);
    }

    @Override
    public int indexOf(Object o) {
        return this.delegate.indexOf(o);
    }

    @Override
    public int lastIndexOf(Object o) {
        return this.delegate.lastIndexOf(o);
    }

    @Override
    public Iterator<E> iterator() {
        return this.delegate.iterator();
    }

    @Override
    public Spliterator<E> spliterator() {
        return this.delegate.spliterator();
    }

    @Override
    public Stream<E> stream() {
        return this.delegate.stream();
    }

    @Override
    public Stream<E> parallelStream() {
        return this.delegate.parallelStream();
    }

    @Override
    public void forEach(Consumer<? super E> action) {
        this.delegate.forEach(action);
    }

    @Override
    public Object[] toArray() {
        return this.delegate.toArray();
    }

    @Override
    public <T> T[] toArray(T[] a) {
        return this.delegate.toArray(a);
    }

    @Override
    public boolean remove(Object o) {
        return o != null && this.delegate.remove(o);
    }

    @Override
    public boolean retainAll(Collection<?> c) {
        return this.delegate.retainAll(c);
    }

    @Override
    public void replaceAll(UnaryOperator<E> operator) {
        this.delegate.replaceAll(v -> Objects.requireNonNull(operator.apply(v)));
    }

    @Override
    public void sort(Comparator<? super E> c) {
        this.delegate.sort(c);
    }

    @Override
    public boolean removeIf(Predicate<? super E> filter) {
        return this.delegate.removeIf(filter);
    }

    @Redirect(method = "Lnet/minecraft/util/NonNullList;clear()V",
            at = @At(value = "INVOKE",
                    target = "Ljava/util/AbstractList;clear()V"),
            allow = 1, require = 1)
    private void ppatches_optimizeNonNullList_clear_clearDelegate(AbstractList<?> _super) {
        this.delegate.clear();
    }
}
