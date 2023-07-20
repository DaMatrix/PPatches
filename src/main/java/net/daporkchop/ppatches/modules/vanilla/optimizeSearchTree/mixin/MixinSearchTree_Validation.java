package net.daporkchop.ppatches.modules.vanilla.optimizeSearchTree.mixin;

import com.google.common.base.Preconditions;
import net.minecraft.client.util.SearchTree;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * @author DaPorkchop_
 */
@Mixin(SearchTree.class)
abstract class MixinSearchTree_Validation<T> {
    @Unique
    private boolean ppatches_optimizeSearchTree_calculated = false;

    @Inject(method = "Lnet/minecraft/client/util/SearchTree;add(Ljava/lang/Object;)V",
            at = @At("HEAD"),
            allow = 1, require = 1)
    private void ppatches_optimizeSearchTree_add_ensureNotCalculated(CallbackInfo ci) {
        Preconditions.checkState(!this.ppatches_optimizeSearchTree_calculated, "recalculate() has already been called on this SearchTree!");
    }

    @Inject(method = "Lnet/minecraft/client/util/SearchTree;recalculate()V",
            at = @At("RETURN"),
            allow = 1, require = 1)
    private void ppatches_optimizeSearchTree_recalculate_setCalculated(CallbackInfo ci) {
        this.ppatches_optimizeSearchTree_calculated = true;
    }

    @Inject(method = "Lnet/minecraft/client/util/SearchTree;search(Ljava/lang/String;)Ljava/util/List;",
            at = @At("HEAD"),
            allow = 1, require = 1)
    private void ppatches_optimizeSearchTree_search_ensureCalculated(CallbackInfoReturnable<List<T>> ci) {
        Preconditions.checkState(this.ppatches_optimizeSearchTree_calculated, "recalculate() hasn't been called yet on this SearchTree!");
    }
}
