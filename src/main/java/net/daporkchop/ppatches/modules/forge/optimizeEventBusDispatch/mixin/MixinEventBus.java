package net.daporkchop.ppatches.modules.forge.optimizeEventBusDispatch.mixin;

import com.google.common.base.Throwables;
import net.daporkchop.ppatches.modules.forge.optimizeEventBusDispatch.util.IMixinListenerList_OptimizeEventBusDispatch;
import net.minecraftforge.fml.common.eventhandler.Event;
import net.minecraftforge.fml.common.eventhandler.EventBus;
import net.minecraftforge.fml.common.eventhandler.IEventExceptionHandler;
import net.minecraftforge.fml.common.eventhandler.IEventListener;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

/**
 * @author DaPorkchop_
 */
@Mixin(value = EventBus.class, remap = false)
abstract class MixinEventBus {
    @Shadow
    private IEventExceptionHandler exceptionHandler;

    @Shadow
    private boolean shutdown;

    @Shadow
    @Final
    private int busID;

    public Throwable ppatches_optimizeEventBusDispatch_handleException(Event event, IEventListener[] listeners, int index, Throwable throwable) {
        this.exceptionHandler.handleException((EventBus) (Object) this, event, listeners, index, throwable);
        Throwables.throwIfUnchecked(throwable);
        throw new RuntimeException(throwable);
    }

    /**
     * @author DaPorkchop_
     * @reason we're replacing the whole method
     */
    @Overwrite
    public boolean post(Event event) throws Throwable {
        if (this.shutdown) {
            return false;
        }

        return (boolean) ((IMixinListenerList_OptimizeEventBusDispatch) event.getListenerList())
                .ppatches_optimizeEventBusDispatch_getGenericInvoker(this.busID)
                .invokeExact((EventBus) (Object) this, event);
    }
}
