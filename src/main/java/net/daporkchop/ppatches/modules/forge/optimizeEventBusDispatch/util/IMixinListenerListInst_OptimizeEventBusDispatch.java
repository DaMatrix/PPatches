package net.daporkchop.ppatches.modules.forge.optimizeEventBusDispatch.util;

import java.lang.invoke.CallSite;
import java.lang.invoke.MethodHandle;

/**
 * @author DaPorkchop_
 */
public interface IMixinListenerListInst_OptimizeEventBusDispatch {
    CallSite ppatches_optimizeEventBusDispatch_getExactCallSite(Class<?> exactEventType);

    MethodHandle ppatches_optimizeEventBusDispatch_getGenericInvoker();
}
