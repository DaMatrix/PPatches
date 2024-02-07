package net.daporkchop.ppatches.modules.forge.optimizeEventBusDispatch.util;

import java.lang.invoke.CallSite;
import java.lang.invoke.MethodType;

/**
 * @author DaPorkchop_
 */
public interface IMixinListenerListInst_OptimizeEventBusDispatch {
    CallSite ppatches_optimizeEventBusDispatch_getCallSite(MethodType exactPostType);
}
