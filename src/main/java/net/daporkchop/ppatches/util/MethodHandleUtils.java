package net.daporkchop.ppatches.util;

import lombok.experimental.UtilityClass;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/**
 * Helper methods for working with {@link MethodHandle}.
 *
 * @author DaPorkchop_
 */
@UtilityClass
public class MethodHandleUtils {
    private static final MethodHandle VOID_IDENTITY;

    static {
        try {
            MethodHandles.Lookup lookup = MethodHandles.lookup();

            VOID_IDENTITY = lookup.findStatic(MethodHandleUtils.class, "voidIdentity", MethodType.methodType(void.class));
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    private static void voidIdentity() {
        //no-op
    }

    /**
     * Produces a method handle which returns its sole argument when invoked.
     * <p>
     * Unlike {@link MethodHandles#identity(Class)}, identity methods of type {@code void} are permitted. An identity method of type {@code void} accepts no arguments and
     * returns {@code void}.
     *
     * @see MethodHandles#identity(Class)
     */
    public static MethodHandle identity(Class<?> type) {
        return type == void.class ? VOID_IDENTITY : MethodHandles.identity(type);
    }
}
