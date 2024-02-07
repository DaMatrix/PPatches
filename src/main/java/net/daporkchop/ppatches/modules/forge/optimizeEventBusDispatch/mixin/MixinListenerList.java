package net.daporkchop.ppatches.modules.forge.optimizeEventBusDispatch.mixin;

import lombok.SneakyThrows;
import net.daporkchop.ppatches.PPatchesMod;
import net.daporkchop.ppatches.modules.forge.optimizeEventBusDispatch.OptimizeEventBusDispatchTransformer;
import net.daporkchop.ppatches.modules.forge.optimizeEventBusDispatch.util.IMixinListenerListInst_OptimizeEventBusDispatch;
import net.daporkchop.ppatches.modules.forge.optimizeEventBusDispatch.util.IMixinListenerList_OptimizeEventBusDispatch;
import net.minecraftforge.fml.common.eventhandler.Event;
import net.minecraftforge.fml.common.eventhandler.EventBus;
import net.minecraftforge.fml.common.eventhandler.IEventListener;
import net.minecraftforge.fml.common.eventhandler.ListenerList;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.invoke.CallSite;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.MutableCallSite;

/**
 * @author DaPorkchop_
 */
@Mixin(value = ListenerList.class, remap = false)
abstract class MixinListenerList implements IMixinListenerList_OptimizeEventBusDispatch {
    //we need to do this gross hack because ListenerListInst is private and can't be access transformered because it's a forge class
    @Unique
    private static final MethodHandle ppatches_optimizeEventBusDispatch_getLists;

    static {
        try {
            ppatches_optimizeEventBusDispatch_getLists = MethodHandles.lookup().unreflectGetter(ListenerList.class.getDeclaredField("lists")).asType(MethodType.methodType(Object[].class, ListenerList.class));
        } catch (Throwable t) {
            throw new AssertionError(t);
        }
    }

    @Override
    @SneakyThrows
    public CallSite ppatches_optimizeEventBusDispatch_getCallSite(EventBus bus, MethodType exactPostType) {
        Object[] lists = (Object[]) ppatches_optimizeEventBusDispatch_getLists.invokeExact(this);
        return ((IMixinListenerListInst_OptimizeEventBusDispatch) lists[((IMixinEventBus) bus).getBusID()]).ppatches_optimizeEventBusDispatch_getCallSite(exactPostType);
    }

    @Mixin(targets = "net.minecraftforge.fml.common.eventhandler.ListenerList$ListenerListInst", remap = false)
    private static abstract class ListenerListInst implements IMixinListenerListInst_OptimizeEventBusDispatch {
        @Unique
        private static final MethodHandle ppatches_optimizeEventBusDispatch_unbound_postEventSlowPath;

        static {
            try {
                ppatches_optimizeEventBusDispatch_unbound_postEventSlowPath = MethodHandles.publicLookup().findStatic(
                                OptimizeEventBusDispatchTransformer.class, "postEventSlowPath",
                                MethodType.methodType(boolean.class, MethodHandle.class, MethodHandle.class, Object.class, Event.class))
                        .bindTo(MethodHandles.lookup().findVirtual(ListenerListInst.class, "getListeners", MethodType.methodType(IEventListener[].class))
                                .asType(MethodType.methodType(IEventListener[].class, Object.class)))
                        .bindTo(MethodHandles.lookup().findSpecial(ListenerListInst.class, "ppatches_optimizeEventBusDispatch_populateCallSite",
                                MethodType.methodType(void.class, IEventListener[].class, MethodHandle.class), ListenerListInst.class));
            } catch (Throwable t) {
                throw new AssertionError(t);
            }
        }

        @Shadow
        private boolean rebuild;
        @Shadow
        private IEventListener[] listeners;

        @Unique
        private MutableCallSite ppatches_optimizeEventBusDispatch_callSite;

        @Dynamic
        @Inject(method = "forceRebuild()V",
                at = @At(value = "FIELD",
                        target = "Lnet/minecraftforge/fml/common/eventhandler/ListenerList$ListenerListInst;rebuild:Z",
                        opcode = Opcodes.PUTFIELD,
                        shift = At.Shift.AFTER),
                allow = 1, require = 1)
        private void ppatches_optimizeEventBusDispatch_forceRebuild_invalidateCallSite(CallbackInfo ci) {
            if (this.ppatches_optimizeEventBusDispatch_callSite != null) {
                //we only need to invalidate the optimized call site if the call site actually exists anywhere
                PPatchesMod.LOGGER.info("Invalidating optimized dispatcher for {}", this.ppatches_optimizeEventBusDispatch_callSite.type().parameterType(0));

                //this.ppatches_optimizeEventBusDispatch_callSite.setTarget(ppatches_optimizeEventBusDispatch_rawBuildCache.bindTo(this));
                this.ppatches_optimizeEventBusDispatch_callSite.setTarget(ppatches_optimizeEventBusDispatch_unbound_postEventSlowPath.bindTo(this).asType(this.ppatches_optimizeEventBusDispatch_callSite.type()));

                //TODO: is this important?
                MutableCallSite.syncAll(new MutableCallSite[]{this.ppatches_optimizeEventBusDispatch_callSite});
            }
        }

        private void ppatches_optimizeEventBusDispatch_populateCallSite(IEventListener[] listeners, MethodHandle optimizedDispatcher) {
            //noinspection ArrayEquality
            if (this.rebuild || this.listeners != listeners) {
                //this list has changed since we started generating the optimized class
                return;
            }

            PPatchesMod.LOGGER.info("Saving optimized dispatcher for {}", this.ppatches_optimizeEventBusDispatch_callSite.type().parameterType(0));

            this.ppatches_optimizeEventBusDispatch_callSite.setTarget(optimizedDispatcher);

            //TODO: is this important?
            MutableCallSite.syncAll(new MutableCallSite[]{this.ppatches_optimizeEventBusDispatch_callSite});
        }

        @Override
        public final CallSite ppatches_optimizeEventBusDispatch_getCallSite(MethodType exactPostType) {
            if (this.ppatches_optimizeEventBusDispatch_callSite == null) {
                synchronized (this) {
                    if (this.ppatches_optimizeEventBusDispatch_callSite == null) {
                        PPatchesMod.LOGGER.info("Building initial CallSite for {}", exactPostType.parameterType(0));
                        this.ppatches_optimizeEventBusDispatch_callSite = new MutableCallSite(ppatches_optimizeEventBusDispatch_unbound_postEventSlowPath.bindTo(this).asType(exactPostType));
                    }
                }
            }
            return this.ppatches_optimizeEventBusDispatch_callSite;
        }
    }
}
