package net.daporkchop.ppatches.util;

import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;
import sun.misc.Unsafe;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;

/**
 * Wrapper around {@code sun.misc.Unsafe} to avoid un-suppressable compile-time warnings.
 *
 * @author DaPorkchop_
 */
@UtilityClass
public class UnsafeWrapper {
    private static final Unsafe UNSAFE;

    static {
        try {
            Class<?> unsafeClass = Class.forName("sun.misc.Unsafe");
            Field unsafeField = unsafeClass.getDeclaredField("theUnsafe");
            unsafeField.setAccessible(true);
            UNSAFE = (Unsafe) unsafeField.get(null);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    @SneakyThrows(InstantiationException.class)
    @SuppressWarnings("unchecked")
    public static <T> T allocateInstance(Class<T> clazz) {
        return (T) UNSAFE.allocateInstance(clazz);
    }

    public static Class<?> defineClass(String name, byte[] data, ClassLoader loader) {
        return UNSAFE.defineClass(name, data, 0, data.length, loader, null);
    }

    public static Class<?> defineAnonymousClass(Class<?> hostClass, byte[] data, Object[] cpPatches) {
        return UNSAFE.defineAnonymousClass(hostClass, data, cpPatches);
    }

    public static void fullFence() {
        UNSAFE.fullFence();
    }

    @SneakyThrows(NoSuchFieldException.class)
    public static long objectFieldOffset(Class<?> clazz, String name) {
        return UNSAFE.objectFieldOffset(clazz.getDeclaredField(name));
    }

    public static long objectFieldOffset(Field f) {
        return UNSAFE.objectFieldOffset(f);
    }

    public static void putBoolean(Object o, long offset, boolean value) {
        UNSAFE.putBoolean(o, offset, value);
    }
}
