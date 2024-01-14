package net.daporkchop.ppatches.modules.extraUtilities2.optimizeItemCaptureHandler.mixin;

import net.daporkchop.ppatches.util.mixin.ext.Delete;
import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.util.NonNullList;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.fml.common.eventhandler.EventBus;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.util.LinkedList;

/**
 * @author DaPorkchop_
 */
@Pseudo
@Mixin(targets = "com.rwtema.extrautils2.eventhandlers.ItemCaptureHandler", remap = false)
abstract class MixinItemCaptureHandler {
    @Unique
    private static final MethodHandle CAPTURE_DROPS;

    static {
        try {
            Method captureDropsMethod = Block.class.getDeclaredMethod("captureDrops", boolean.class);
            captureDropsMethod.setAccessible(true);
            CAPTURE_DROPS = MethodHandles.publicLookup().unreflect(captureDropsMethod).bindTo(Blocks.AIR);
        } catch (Throwable e) {
            throw new RuntimeException("PPatches: extraUtilities2.optimizeItemCaptureHandler failed to initialize", e);
        }
    }

    @Dynamic
    @Inject(method = "Lcom/rwtema/extrautils2/eventhandlers/ItemCaptureHandler;startCapturing()V",
            at = @At("RETURN"),
            allow = 1, require = 1)
    private static void ppatches_optimizeItemCaptureHandler_startCapturing_useCaptureDropsForBlocks(CallbackInfo ci) throws Throwable {
        @SuppressWarnings("unchecked")
        NonNullList<ItemStack> unused = (NonNullList<ItemStack>) CAPTURE_DROPS.invokeExact(true);
    }

    @Dynamic
    @Inject(method = "Lcom/rwtema/extrautils2/eventhandlers/ItemCaptureHandler;stopCapturing()Ljava/util/LinkedList;",
            at = @At(value = "INVOKE",
                    target = "Ljava/lang/ThreadLocal;set(Ljava/lang/Object;)V",
                    shift = At.Shift.AFTER),
            locals = LocalCapture.CAPTURE_FAILHARD,
            allow = 1, require = 1)
    private static void ppatches_optimizeItemCaptureHandler_stopCapturing_useCaptureDropsForBlocks(CallbackInfoReturnable<LinkedList<ItemStack>> ci, LinkedList<ItemStack> items) throws Throwable {
        @SuppressWarnings("unchecked")
        NonNullList<ItemStack> blockDrops = (NonNullList<ItemStack>) CAPTURE_DROPS.invokeExact(false);

        items.addAll(blockDrops);
    }

    //delete the onItemJoin event handler since it's not actually needed
    @Delete
    @Dynamic
    @Shadow
    @SuppressWarnings("unused")
    public abstract void onItemJoin(EntityJoinWorldEvent event);

    //don't register this class to the event bus since it no longer has any event handlers
    @Dynamic
    @Redirect(method = "<clinit>",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraftforge/fml/common/eventhandler/EventBus;register(Ljava/lang/Object;)V"),
            allow = 1, require = 1)
    private static void ppatches_optimizeItemCaptureHandler_$clinit$_dontRegisterToEventBus(EventBus bus, Object handler) {
        //no-op
    }
}
