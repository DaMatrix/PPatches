package net.daporkchop.ppatches.modules.vanilla.optimizeSearchTree.mixin;

import lombok.SneakyThrows;
import net.daporkchop.ppatches.PPatchesMod;
import net.minecraft.client.util.SearchTree;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinTask;
import java.util.concurrent.ForkJoinWorkerThread;

/**
 * @author DaPorkchop_
 */
@Mixin(value = SearchTree.class, priority = 1001) //we want our injections to go after the ones from the _Validation mixin
abstract class MixinSearchTree_AsyncCalculation<T> {
    @Shadow
    public abstract void recalculate();

    @Unique
    private ForkJoinTask<?> ppatches_optimizeSearchTree_recalculateTask;

    @Inject(method = "Lnet/minecraft/client/util/SearchTree;recalculate()V",
            at = @At("HEAD"),
            cancellable = true,
            allow = 1, require = 1)
    @SneakyThrows(InterruptedException.class)
    private synchronized void ppatches_optimizeSearchTree_recalculate_beginAsyncCalculation(CallbackInfo ci) {
        if (!(Thread.currentThread() instanceof ForkJoinWorkerThread)) {
            while (this.ppatches_optimizeSearchTree_recalculateTask != null && !this.ppatches_optimizeSearchTree_recalculateTask.isDone()) { //wait for an existing task to complete, if any
                PPatchesMod.LOGGER.trace("SearchTree {} already has an async calculation task, waiting for it to complete...", this);
                this.wait();
            }

            PPatchesMod.LOGGER.trace("Creating async SearchTree calculation task for {}", this);
            this.ppatches_optimizeSearchTree_recalculateTask = ForkJoinPool.commonPool().submit(this::recalculate);
            ci.cancel();
        } else {
            PPatchesMod.LOGGER.trace("Running SearchTree calculation for {} asynchronously", this);
        }
    }

    @Inject(method = "Lnet/minecraft/client/util/SearchTree;recalculate()V",
            at = @At("RETURN"),
            allow = 1, require = 1)
    private synchronized void ppatches_optimizeSearchTree_recalculate_notifyAsyncCalculationComplete(CallbackInfo ci) {
        PPatchesMod.LOGGER.trace("Asynchronous SearchTree calculation for {} complete", this);
        this.notifyAll();
    }

    @Inject(method = "Lnet/minecraft/client/util/SearchTree;search(Ljava/lang/String;)Ljava/util/List;",
            at = @At("HEAD"),
            allow = 1, require = 1)
    private void ppatches_optimizeSearchTree_search_waitForAsyncCalculation(CallbackInfoReturnable<List<T>> ci) {
        this.ppatches_optimizeSearchTree_recalculateTask.join();
    }
}
