package net.daporkchop.ppatches.modules.forge.optimizeEventBusDispatch.util;

import net.minecraftforge.fml.common.eventhandler.EventBus;

import java.lang.invoke.CallSite;
import java.lang.invoke.MethodType;

/**
 * @author DaPorkchop_
 */
public interface IMixinListenerList_OptimizeEventBusDispatch {
    CallSite ppatches_optimizeEventBusDispatch_getCallSite(EventBus bus, MethodType exactPostType);
}
