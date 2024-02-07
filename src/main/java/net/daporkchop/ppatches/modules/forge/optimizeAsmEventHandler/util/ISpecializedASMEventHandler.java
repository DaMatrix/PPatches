package net.daporkchop.ppatches.modules.forge.optimizeAsmEventHandler.util;

import java.lang.invoke.MethodHandle;

/**
 * @author DaPorkchop_
 */
public interface ISpecializedASMEventHandler {
    Class<?> exactEventClass();

    /**
     * @return a {@link MethodHandle} with signature {@code void (exactEventClass)}
     */
    MethodHandle getExactInvoker();
}
