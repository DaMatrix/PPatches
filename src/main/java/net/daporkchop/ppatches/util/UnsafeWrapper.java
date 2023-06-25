package net.daporkchop.ppatches.util;

import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;

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
    private static final MethodHandle ALLOCATE_INSTANCE;
    private static final MethodHandle DEFINE_ANONYMOUS_CLASS;

    static {
        try {
            Class<?> unsafeClass = Class.forName("sun.misc.Unsafe");
            Field unsafeField = unsafeClass.getDeclaredField("theUnsafe");
            unsafeField.setAccessible(true);
            Object unsafeInstance = unsafeField.get(null);

            ALLOCATE_INSTANCE = MethodHandles.publicLookup().findVirtual(unsafeClass, "allocateInstance", MethodType.methodType(Object.class, Class.class)).bindTo(unsafeInstance);
            DEFINE_ANONYMOUS_CLASS = MethodHandles.publicLookup().findVirtual(unsafeClass, "defineAnonymousClass", MethodType.methodType(Class.class, Class.class, byte[].class, Object[].class)).bindTo(unsafeInstance);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    @SneakyThrows
    @SuppressWarnings("unchecked")
    public static <T> T allocateInstance(Class<T> clazz) {
        return (T) ALLOCATE_INSTANCE.invokeExact(clazz);
    }

    @SneakyThrows
    public static Class<?> defineAnonymousClass(Class<?> hostClass, byte[] data, Object[] cpPatches) {
        return (Class<?>) DEFINE_ANONYMOUS_CLASS.invokeExact(hostClass, data, cpPatches);
    }
}
