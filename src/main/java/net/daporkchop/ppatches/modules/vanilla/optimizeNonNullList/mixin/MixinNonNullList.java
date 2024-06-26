package net.daporkchop.ppatches.modules.vanilla.optimizeNonNullList.mixin;

import com.google.common.base.Preconditions;
import net.daporkchop.ppatches.util.mixin.ext.Delete;
import net.minecraft.util.NonNullList;
import org.apache.commons.lang3.Validate;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Array;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Spliterator;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

/**
 * @author DaPorkchop_
 */
@Mixin(NonNullList.class)
abstract class MixinNonNullList<E> extends AbstractList<E> {
    @Unique
    @SuppressWarnings("ArraysAsListWithZeroOrOneArgument")
    private static final Class<?> ppatches_optimizeNonNullList_Arrays_asList_class = Arrays.asList().getClass();

    @Delete(removeInstanceInitializer = true)
    @Shadow
    @Final
    private List<E> delegate;

    @Shadow
    @Final
    private E defaultElement;

    @Unique
    private E[] ppatches_optimizeNonNullList_arr;
    @Unique
    private int ppatches_optimizeNonNullList_size;

    /**
     * This is {@code true} if the list may be resized.
     * <p>
     * Resizable lists behave (more or less) as if backed by an {@link ArrayList}, while non-resizable lists behave as if backed by a list supplied by
     * {@link Arrays#asList(Object[])}.
     */
    @Unique
    private boolean ppatches_optimizeNonNullList_resizable;

    @SuppressWarnings("unchecked")
    @Inject(method = "<init>(Ljava/util/List;Ljava/lang/Object;)V",
            at = @At("RETURN"),
            allow = 1, require = 1)
    private void ppatches_optimizeNonNullList_$init$_checkArgs(List<E> delegateIn, E defaultElement, CallbackInfo ci) {
        Class<?> delegateClass = delegateIn.getClass();
        if (delegateClass == ArrayList.class) {
            Preconditions.checkArgument(delegateIn.isEmpty(), "resizable NonNullList must be constructed with an empty ArrayList");
            Preconditions.checkArgument(defaultElement == null, "resizable NonNullList must be constructed with a null default element");

            this.ppatches_optimizeNonNullList_arr = (E[]) ppatches_optimizeNonNullList_DEFAULTCAPACITY_EMPTY_ELEMENTDATA;
            this.ppatches_optimizeNonNullList_resizable = true;
        } else {
            Preconditions.checkArgument(delegateClass == ppatches_optimizeNonNullList_Arrays_asList_class, "non-resizable NonNullList must be constructed with a list supplied by Arrays.asList(): %s", delegateClass);
            //this would be a nice check, but for some unfathomable reason Mekanism overrides this class for no reason other than to construct it with nonsensical parameters
            // which i now have to handle in order to keep stuff from breaking.
            // Preconditions.checkArgument(defaultElement != null, "non-resizable NonNullList must be constructed with a non-null default element");

            this.ppatches_optimizeNonNullList_arr = (E[]) delegateIn.toArray();
            this.ppatches_optimizeNonNullList_size = this.ppatches_optimizeNonNullList_arr.length;
        }
    }

    /**
     * @author DaPorkchop_
     * @reason we're replacing the whole method
     */
    @Override
    @Overwrite(remap = false)
    public E get(int index) {
        this.ppatches_optimizeNonNullList_rangeCheck(index);
        return this.ppatches_optimizeNonNullList_arr[index];
    }

    /**
     * @author DaPorkchop_
     * @reason we're replacing the whole method
     */
    @Override
    @Overwrite(remap = false)
    public E set(int index, E element) {
        Validate.notNull(element);
        this.ppatches_optimizeNonNullList_rangeCheck(index);

        E oldValue = this.ppatches_optimizeNonNullList_arr[index];
        this.ppatches_optimizeNonNullList_arr[index] = element;
        return oldValue;
    }

    @Override
    public boolean add(E element) {
        Validate.notNull(element);
        this.ppatches_optimizeNonNullList_resizableCheck();
        this.ppatches_optimizeNonNullList_ensureCapacityInternal(this.ppatches_optimizeNonNullList_size + 1);

        this.ppatches_optimizeNonNullList_arr[this.ppatches_optimizeNonNullList_size++] = element;
        return true;
    }

    /**
     * @author DaPorkchop_
     * @reason we're replacing the whole method
     */
    @Override
    @Overwrite(remap = false)
    public void add(int index, E element) {
        Validate.notNull(element);
        this.ppatches_optimizeNonNullList_resizableCheck();
        this.ppatches_optimizeNonNullList_rangeCheckForAdd(index);

        this.ppatches_optimizeNonNullList_ensureCapacityInternal(this.ppatches_optimizeNonNullList_size + 1);
        System.arraycopy(this.ppatches_optimizeNonNullList_arr, index, this.ppatches_optimizeNonNullList_arr, index + 1, this.ppatches_optimizeNonNullList_size - index);
        this.ppatches_optimizeNonNullList_arr[index] = element;
        this.ppatches_optimizeNonNullList_size++;
    }

    /**
     * @author DaPorkchop_
     * @reason we're replacing the whole method
     */
    @Override
    @Overwrite(remap = false)
    public E remove(int index) {
        this.ppatches_optimizeNonNullList_resizableCheck();
        this.ppatches_optimizeNonNullList_rangeCheck(index);
        E oldValue = this.ppatches_optimizeNonNullList_arr[index];

        this.ppatches_optimizeNonNullList_fastRemove(index);
        return oldValue;
    }

    /**
     * @author DaPorkchop_
     * @reason we're replacing the whole method
     */
    @Override
    @Overwrite(remap = false)
    public int size() {
        return this.ppatches_optimizeNonNullList_size;
    }

    /**
     * @author DaPorkchop_
     * @reason we're replacing the whole method
     */
    @Override
    @Overwrite(remap = false)
    public void clear() {
        if (this.ppatches_optimizeNonNullList_size > 0) {
            if (!this.ppatches_optimizeNonNullList_resizable && this.defaultElement == null) {
                //the list isn't resizable (it's backed by an Arrays.asList()), but the default element is null! vanilla code would
                // delegate to super.clear() in this case, which would eventually cause the list from Arrays.asList() to throw an
                // UnsupportedOperationException().
                throw new UnsupportedOperationException();
            }

            //fill the whole list with the default element (null for resizable lists)
            Arrays.fill(this.ppatches_optimizeNonNullList_arr, 0, this.ppatches_optimizeNonNullList_size, this.defaultElement);

            if (this.ppatches_optimizeNonNullList_resizable) { //this is a resizable list, we need to reset the size to 0
                this.ppatches_optimizeNonNullList_size = 0;
            }
        }
    }

    @Override
    public boolean isEmpty() {
        return this.ppatches_optimizeNonNullList_size == 0;
    }

    @Override
    public boolean contains(Object o) {
        return this.indexOf(o) >= 0;
    }

    @Override
    public int indexOf(Object o) {
        if (o != null) {
            E[] arr = this.ppatches_optimizeNonNullList_arr;
            int size = this.ppatches_optimizeNonNullList_size;
            for (int i = 0; i < size; i++) {
                if (o.equals(arr[i])) {
                    return i;
                }
            }
        }
        return -1;
    }

    @Override
    public int lastIndexOf(Object o) {
        if (o != null) {
            E[] arr = this.ppatches_optimizeNonNullList_arr;
            int size = this.ppatches_optimizeNonNullList_size;
            for (int i = size - 1; i >= 0; i--) {
                if (o.equals(arr[i])) {
                    return i;
                }
            }
        }
        return -1;
    }

    @Override
    public Iterator<E> iterator() {
        return new Iterator<E>() {
            int pos = 0;
            int last = -1;

            @Override
            public boolean hasNext() {
                return this.pos < MixinNonNullList.this.ppatches_optimizeNonNullList_size;
            }

            @Override
            public E next() {
                if (!this.hasNext()) {
                    throw new NoSuchElementException();
                }
                return MixinNonNullList.this.ppatches_optimizeNonNullList_arr[this.last = this.pos++];
            }

            @Override
            public void remove() {
                MixinNonNullList.this.ppatches_optimizeNonNullList_resizableCheck();
                if (this.last < 0) {
                    throw new IllegalStateException();
                }
                MixinNonNullList.this.remove(this.last);
                if (this.last < this.pos) {
                    this.pos--;
                }
                this.last = -1;
            }
        };
    }

    @Override
    public Spliterator<E> spliterator() {
        return Arrays.spliterator(this.ppatches_optimizeNonNullList_arr, 0, this.ppatches_optimizeNonNullList_size);
    }

    @Override
    public void forEach(Consumer<? super E> action) {
        Objects.requireNonNull(action);

        E[] arr = this.ppatches_optimizeNonNullList_arr;
        int size = this.ppatches_optimizeNonNullList_size;
        for (int i = 0; i < size; i++) {
            action.accept(arr[i]);
        }
    }

    @Override
    public Object[] toArray() {
        return Arrays.copyOf(this.ppatches_optimizeNonNullList_arr, this.ppatches_optimizeNonNullList_size);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> T[] toArray(T[] a) {
        E[] arr = this.ppatches_optimizeNonNullList_arr;
        int size = this.ppatches_optimizeNonNullList_size;

        if (a.length < size) {
            a = (T[]) Array.newInstance(a.getClass().getComponentType(), size);
        } else if (a.length > size) { //null-terminate
            a[size] = null;
        }
        System.arraycopy(arr, 0, a, 0, size);
        if (size < a.length) { //null-terminate
            a[size] = null;
        }
        return a;
    }

    @Override
    public boolean addAll(Collection<? extends E> c) {
        this.ppatches_optimizeNonNullList_resizableCheck();

        Object[] src = c.toArray();
        if (src.length > 0) {
            this.ppatches_optimizeNonNullList_ensureCapacityInternal(this.ppatches_optimizeNonNullList_size + src.length);
            System.arraycopy(src, 0, this.ppatches_optimizeNonNullList_arr, this.ppatches_optimizeNonNullList_size, src.length);
            this.ppatches_optimizeNonNullList_size += src.length;
            return true;
        } else {
            return false;
        }
    }

    @Override
    public boolean remove(Object o) {
        this.ppatches_optimizeNonNullList_resizableCheck();

        if (o != null) {
            for (int i = 0; i < this.ppatches_optimizeNonNullList_size; i++) {
                if (o.equals(this.ppatches_optimizeNonNullList_arr[i])) {
                    this.ppatches_optimizeNonNullList_fastRemove(i);
                    return true;
                }
            }
        }
        return false;
    }

    @Unique
    private void ppatches_optimizeNonNullList_fastRemove(int index) {
        int numMoved = this.ppatches_optimizeNonNullList_size - index - 1;
        if (numMoved > 0) {
            System.arraycopy(this.ppatches_optimizeNonNullList_arr, index + 1, this.ppatches_optimizeNonNullList_arr, index, numMoved);
        }
        this.ppatches_optimizeNonNullList_arr[--this.ppatches_optimizeNonNullList_size] = null;
    }

    @Override
    public void replaceAll(UnaryOperator<E> operator) {
        Objects.requireNonNull(operator);
        E[] arr = this.ppatches_optimizeNonNullList_arr;
        int size = this.ppatches_optimizeNonNullList_size;

        for (int i = 0; i < size; i++) {
            arr[i] = Validate.notNull(operator.apply(arr[i]));
        }
    }

    @Override
    public void sort(Comparator<? super E> c) {
        Arrays.sort(this.ppatches_optimizeNonNullList_arr, 0, this.ppatches_optimizeNonNullList_size, c);
    }

    @Unique
    private void ppatches_optimizeNonNullList_resizableCheck() {
        if (!this.ppatches_optimizeNonNullList_resizable) {
            throw new UnsupportedOperationException("this list may not be resized!");
        }
    }

    @Unique
    private void ppatches_optimizeNonNullList_rangeCheck(int index) {
        if (index >= this.ppatches_optimizeNonNullList_size) {
            throw new IndexOutOfBoundsException(this.ppatches_optimizeNonNullList_outOfBoundsMsg(index));
        }
    }

    @Unique
    private void ppatches_optimizeNonNullList_rangeCheckForAdd(int index) {
        if (index > this.ppatches_optimizeNonNullList_size || index < 0) {
            throw new IndexOutOfBoundsException(this.ppatches_optimizeNonNullList_outOfBoundsMsg(index));
        }
    }

    @Unique
    private String ppatches_optimizeNonNullList_outOfBoundsMsg(int index) {
        return "Index: " + index + ", Size: " + this.ppatches_optimizeNonNullList_size;
    }

    /**
     * Default initial capacity.
     */
    @Unique
    private static final int ppatches_optimizeNonNullList_DEFAULT_CAPACITY = 10;

    /**
     * Shared empty array instance used for default sized empty instances. We
     * distinguish this from EMPTY_ELEMENTDATA to know how much to inflate when
     * first element is added.
     */
    @Unique
    private static final Object[] ppatches_optimizeNonNullList_DEFAULTCAPACITY_EMPTY_ELEMENTDATA = {};

    @Unique
    private void ppatches_optimizeNonNullList_ensureCapacityInternal(int minCapacity) {
        //noinspection ArrayEquality
        if (this.ppatches_optimizeNonNullList_arr == ppatches_optimizeNonNullList_DEFAULTCAPACITY_EMPTY_ELEMENTDATA) {
            minCapacity = Math.max(ppatches_optimizeNonNullList_DEFAULT_CAPACITY, minCapacity);
        }

        int oldCapacity = this.ppatches_optimizeNonNullList_arr.length;
        if (oldCapacity < minCapacity) {
            int newCapacity = (int) Math.max(Math.min((long) (oldCapacity + (oldCapacity >> 1)), it.unimi.dsi.fastutil.Arrays.MAX_ARRAY_SIZE), minCapacity);
            this.ppatches_optimizeNonNullList_arr = Arrays.copyOf(this.ppatches_optimizeNonNullList_arr, newCapacity);
        }
    }
}
