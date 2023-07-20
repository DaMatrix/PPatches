package net.daporkchop.ppatches.modules.vanilla.optimizeSearchTree.mixin;

import com.google.common.base.Preconditions;
import net.daporkchop.ppatches.PPatchesMod;
import net.minecraft.client.util.SuffixArray;
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

/**
 * @author DaPorkchop_
 */
@Mixin(value = SuffixArray.class, priority = 1001) //we want our injections to go after the ones from the _Validation mixin
abstract class MixinSuffixArray_AsyncGeneration<T> {
    @Shadow
    public abstract void generate();

    @Unique
    private ForkJoinTask<?> ppatches_optimizeSearchTree_asyncGenerationTask;

    @Inject(method = "Lnet/minecraft/client/util/SuffixArray;add(Ljava/lang/Object;Ljava/lang/String;)V",
            at = @At("HEAD"),
            allow = 1, require = 1)
    private void ppatches_optimizeSearchTree_add_ensureAsyncGenerationNotStarted(CallbackInfo ci) {
        Preconditions.checkState(this.ppatches_optimizeSearchTree_asyncGenerationTask == null, "This SuffixArray has already started generation! %s", this);
    }

    @Inject(method = "Lnet/minecraft/client/util/SuffixArray;generate()V",
            at = @At("HEAD"),
            cancellable = true,
            allow = 1, require = 1)
    private void ppatches_optimizeSearchTree_generate_beginAsyncExecution(CallbackInfo ci) {
        if (this.ppatches_optimizeSearchTree_asyncGenerationTask == null) {
            PPatchesMod.LOGGER.trace("Creating async SuffixArray generation task for {}", this);
            this.ppatches_optimizeSearchTree_asyncGenerationTask = ForkJoinPool.commonPool().submit(this::generate);
            ci.cancel();
        } else {
            PPatchesMod.LOGGER.trace("Running SuffixArray generation for {} asynchronously", this);
        }
    }

    @Inject(method = "Lnet/minecraft/client/util/SuffixArray;generate()V",
            at = @At("RETURN"),
            allow = 1, require = 1)
    private void ppatches_optimizeSearchTree_generate_notifyAsyncExecutionComplete(CallbackInfo ci) {
        PPatchesMod.LOGGER.trace("Asynchronous SuffixArray generation for {} complete", this);
    }

    @Inject(method = "Lnet/minecraft/client/util/SuffixArray;search(Ljava/lang/String;)Ljava/util/List;",
            at = @At("HEAD"),
            allow = 1, require = 1)
    private void ppatches_optimizeSearchTree_search_waitForAsyncCompletion(CallbackInfoReturnable<List<T>> ci) {
        this.ppatches_optimizeSearchTree_asyncGenerationTask.join();
    }
}
