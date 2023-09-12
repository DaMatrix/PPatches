package net.daporkchop.ppatches.util;

import lombok.experimental.UtilityClass;

import javax.annotation.Nullable;
import java.lang.reflect.Array;
import java.util.Arrays;
import java.util.Objects;

/**
 * Copy-on-write array update methods which always return a copy of the original array contents.
 *
 * @author DaPorkchop_
 */
@UtilityClass
public class COWArrayUtils {
    public static <T> T[] set(T[] array, int index, T element) {
        T[] result = array.clone();
        result[index] = element;
        return result;
    }

    public static <T> T[] insert(T[] array, int index, T element) {
        T[] result = Arrays.copyOf(array, array.length + 1);
        result[index] = element;
        System.arraycopy(array, index, result, index + 1, array.length - index);
        return result;
    }

    public static <T> T[] insert(T[] array, int index,T... elements) {
        T[] result = Arrays.copyOf(array, array.length + elements.length);
        System.arraycopy(elements, 0, result, index, elements.length);
        System.arraycopy(array, index, result, index + elements.length, array.length - index);
        return result;
    }

    public static <T> T[] remove(T[] array, int index) {
        T[] result = Arrays.copyOf(array, array.length - 1);
        System.arraycopy(array, index + 1, result, index, result.length - index);
        return result;
    }

    public static <T> T[] remove(T[] array, int index, int count) {
        T[] result = Arrays.copyOf(array, array.length - count);
        System.arraycopy(array, index + count, result, index, result.length - index - count + 1);
        return result;
    }

    public static <T> T[] append(T[] array, T element) {
        return insert(array, array.length, element);
    }

    public static <T> T[] concat(T[] a, T[] b) {
        return insert(a, a.length, b);
    }

    public static <T> T[] concat(T[]... arrays) {
        assert arrays.length > 0 : "at least one array must be given!";

        int totalLength = 0;
        for (T[] array : arrays) {
            totalLength += array.length;
        }

        T[] result = Arrays.copyOf(arrays[0], totalLength);
        for (int i = 1, writerIndex = arrays[0].length; i < arrays.length; writerIndex += arrays[i++].length) {
            System.arraycopy(arrays[i], 0, result, writerIndex, arrays[i].length);
        }
        return result;
    }

    //
    // inlined COWArrayList
    //

    public static <T> boolean listIsEmpty(@Nullable T[] list) {
        return list == null;
    }

    public static <T> boolean listContains(@Nullable T[] list, T element) {
        return listIndexOf(list, element) >= 0;
    }

    public static <T> int listIndexOf(@Nullable T[] list, T element) {
        if (!listIsEmpty(list)) {
            for (int i = 0; i < list.length; i++) {
                if (Objects.equals(element, list[i])) {
                    return i;
                }
            }
        }
        return -1;
    }

    public static <T> T[] listAdd(Class<T> componentType, @Nullable T[] list, T element) {
        if (listIsEmpty(list)) {
            @SuppressWarnings("unchecked")
            T[] arr = (T[]) Array.newInstance(componentType, 1);
            arr[0] = element;
            return arr;
        } else {
            return append(list, element);
        }
    }

    public static <T> T[] listRemove(@Nullable T[] list, T element) {
        int index = listIndexOf(list, element);
        if (index < 0) { //element isn't in the list, do nothing
            return list;
        } else if (list.length == 1) { //element is the only element in the list, return empty list
            return null;
        } else {
            return remove(list, index);
        }
    }
}
