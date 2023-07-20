package net.daporkchop.ppatches.modules.vanilla.optimizeSearchTree.mixin;

import com.google.common.base.Preconditions;
import net.minecraft.client.util.SuffixArray;
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
@Mixin(SuffixArray.class)
abstract class MixinSuffixArray_Validation<T> {
    @Unique
    private boolean ppatches_optimizeSearchTree_generated = false;

    @Inject(
            method = {
                    "Lnet/minecraft/client/util/SuffixArray;add(Ljava/lang/Object;Ljava/lang/String;)V",
                    "Lnet/minecraft/client/util/SuffixArray;generate()V",
            },
            at = @At("HEAD"),
            allow = 2, require = 2)
    private void ppatches_optimizeSearchTree_ensureNotGenerated(CallbackInfo ci) {
        Preconditions.checkState(!this.ppatches_optimizeSearchTree_generated, "generate() has already been called on this SuffixArray!");
    }

    @Inject(method = "Lnet/minecraft/client/util/SuffixArray;generate()V",
            at = @At("RETURN"),
            allow = 1, require = 1)
    private void ppatches_optimizeSearchTree_generate_setGenerated(CallbackInfo ci) {
        this.ppatches_optimizeSearchTree_generated = true;
    }

    @Inject(method = "Lnet/minecraft/client/util/SuffixArray;search(Ljava/lang/String;)Ljava/util/List;",
            at = @At("HEAD"),
            allow = 1, require = 1)
    private void ppatches_optimizeSearchTree_search_ensureGenerated(CallbackInfoReturnable<List<T>> ci) {
        Preconditions.checkState(this.ppatches_optimizeSearchTree_generated, "generate() hasn't been called yet on this SuffixArray!");
    }
}
