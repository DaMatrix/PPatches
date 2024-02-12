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
    @Delete(removeInstanceInitializer = true)
    @Shadow
    @Final
    private List<E> delegate;

    @Shadow
    @Final
    private E defaultElement;

    private E[] arr;
    private int size;

    @SuppressWarnings("unchecked")
    @Inject(method = "<init>(Ljava/util/List;Ljava/lang/Object;)V",
            at = @At("RETURN"),
            allow = 1, require = 1)
    private void ppatches_optimizeNonNullList_$init$_checkArgs(List<E> delegateIn, E defaultElement, CallbackInfo ci) {
        if (delegateIn instanceof ArrayList) {
            Preconditions.checkArgument(delegateIn.isEmpty(), "resizable NonNullList must be constructed with an empty ArrayList");
            Preconditions.checkArgument(defaultElement == null, "resizable NonNullList must be constructed with a null default element");

            this.arr = (E[]) DEFAULTCAPACITY_EMPTY_ELEMENTDATA;
        } else {
            Preconditions.checkArgument(delegateIn.getClass() == Arrays.asList().getClass(), "non-resizable NonNullList must be constructed with a list supplied by Arrays.asList()");
            Preconditions.checkArgument(defaultElement != null, "non-resizable NonNullList must be constructed with a non-null default element");

            this.arr = (E[]) delegateIn.toArray();
            this.size = this.arr.length;
        }
    }

    /**
     * @author DaPorkchop_
     * @reason we're replacing the whole method
     */
    @Override
    @Overwrite(remap = false)
    public E get(int index) {
        this.rangeCheck(index);
        return this.arr[index];
    }

    /**
     * @author DaPorkchop_
     * @reason we're replacing the whole method
     */
    @Override
    @Overwrite(remap = false)
    public E set(int index, E element) {
        Validate.notNull(element);
        this.rangeCheck(index);

        E oldValue = this.arr[index];
        this.arr[index] = element;
        return oldValue;
    }

    @Override
    public boolean add(E element) {
        Validate.notNull(element);
        this.resizableCheck();
        this.ensureCapacityInternal(this.size + 1);

        this.arr[this.size++] = element;
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
        this.resizableCheck();
        this.rangeCheckForAdd(index);

        this.ensureCapacityInternal(this.size + 1);
        System.arraycopy(this.arr, index, this.arr, index + 1, this.size - index);
        this.arr[index] = element;
        this.size++;
    }

    /**
     * @author DaPorkchop_
     * @reason we're replacing the whole method
     */
    @Override
    @Overwrite(remap = false)
    public E remove(int index) {
        this.resizableCheck();
        this.rangeCheck(index);
        E oldValue = this.arr[index];

        this.fastRemove(index);
        return oldValue;
    }

    /**
     * @author DaPorkchop_
     * @reason we're replacing the whole method
     */
    @Override
    @Overwrite(remap = false)
    public int size() {
        return this.size;
    }

    /**
     * @author DaPorkchop_
     * @reason we're replacing the whole method
     */
    @Override
    @Overwrite(remap = false)
    public void clear() {
        if (this.size > 0) {
            //fill the whole list with the default element (null for resizable lists)
            Arrays.fill(this.arr, 0, this.size, this.defaultElement);

            if (this.defaultElement == null) { //this is a resizable list, we need to reset the size to 0
                this.size = 0;
            }
        }
    }

    @Override
    public boolean isEmpty() {
        return this.size == 0;
    }

    @Override
    public boolean contains(Object o) {
        return this.indexOf(o) >= 0;
    }

    @Override
    public int indexOf(Object o) {
        if (o != null) {
            E[] arr = this.arr;
            int size = this.size;
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
            E[] arr = this.arr;
            int size = this.size;
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
                return this.pos < MixinNonNullList.this.size;
            }

            @Override
            public E next() {
                if (!this.hasNext()) {
                    throw new NoSuchElementException();
                }
                return MixinNonNullList.this.arr[this.last = this.pos++];
            }

            @Override
            public void remove() {
                MixinNonNullList.this.resizableCheck();
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
        return Arrays.spliterator(this.arr, 0, this.size);
    }

    @Override
    public void forEach(Consumer<? super E> action) {
        Objects.requireNonNull(action);

        E[] arr = this.arr;
        int size = this.size;
        for (int i = 0; i < size; i++) {
            action.accept(arr[i]);
        }
    }

    @Override
    public Object[] toArray() {
        return Arrays.copyOf(this.arr, this.size);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> T[] toArray(T[] a) {
        E[] arr = this.arr;
        int size = this.size;

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
        this.resizableCheck();

        Object[] src = c.toArray();
        if (src.length > 0) {
            this.ensureCapacityInternal(this.size + src.length);
            System.arraycopy(src, 0, this.arr, this.size, src.length);
            this.size += src.length;
            return true;
        } else {
            return false;
        }
    }

    @Override
    public boolean remove(Object o) {
        this.resizableCheck();

        if (o != null) {
            for (int i = 0; i < this.size; i++) {
                if (o.equals(this.arr[i])) {
                    this.fastRemove(i);
                    return true;
                }
            }
        }
        return false;
    }

    private void fastRemove(int index) {
        int numMoved = this.size - index - 1;
        if (numMoved > 0) {
            System.arraycopy(this.arr, index + 1, this.arr, index, numMoved);
        }
        this.arr[--this.size] = null;
    }

    @Override
    public void replaceAll(UnaryOperator<E> operator) {
        Objects.requireNonNull(operator);
        E[] arr = this.arr;
        int size = this.size;

        for (int i = 0; i < size; i++) {
            arr[i] = Validate.notNull(operator.apply(arr[i]));
        }
    }

    @Override
    public void sort(Comparator<? super E> c) {
        Arrays.sort(this.arr, 0, this.size, c);
    }

    @Unique
    private void resizableCheck() {
        if (this.defaultElement != null) {
            throw new UnsupportedOperationException("this list may not be resized!");
        }
    }

    @Unique
    private void rangeCheck(int index) {
        if (index >= this.size) {
            throw new IndexOutOfBoundsException(this.outOfBoundsMsg(index));
        }
    }

    @Unique
    private void rangeCheckForAdd(int index) {
        if (index > this.size || index < 0) {
            throw new IndexOutOfBoundsException(this.outOfBoundsMsg(index));
        }
    }

    @Unique
    private String outOfBoundsMsg(int index) {
        return "Index: " + index + ", Size: " + this.size;
    }

    /**
     * Default initial capacity.
     */
    @Unique
    private static final int DEFAULT_CAPACITY = 10;

    /**
     * Shared empty array instance used for default sized empty instances. We
     * distinguish this from EMPTY_ELEMENTDATA to know how much to inflate when
     * first element is added.
     */
    @Unique
    private static final Object[] DEFAULTCAPACITY_EMPTY_ELEMENTDATA = {};

    private void ensureCapacityInternal(int minCapacity) {
        //noinspection ArrayEquality
        if (this.arr == DEFAULTCAPACITY_EMPTY_ELEMENTDATA) {
            minCapacity = Math.max(DEFAULT_CAPACITY, minCapacity);
        }

        int oldCapacity = this.arr.length;
        if (oldCapacity < minCapacity) {
            int newCapacity = (int) Math.max(Math.min((long) (oldCapacity + (oldCapacity >> 1)), it.unimi.dsi.fastutil.Arrays.MAX_ARRAY_SIZE), minCapacity);
            this.arr = Arrays.copyOf(this.arr, newCapacity);
        }
    }
}
