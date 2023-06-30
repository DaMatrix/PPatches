package net.daporkchop.ppatches.modules.vanilla.optimizeSearchTree.asm;

import net.minecraft.client.util.SearchTree;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * @author DaPorkchop_
 */
@Mixin(SearchTree.class)
abstract class MixinSearchTree_NoIndexOnAdd<T> {
    /**
     * Avoid calling {@link SearchTree#index(Object)} from {@link SearchTree#add(Object)}, as the indexed data is completely discarded when the search tree is
     * {@link SearchTree#recalculate() recalculated} and attempting to {@link SearchTree#search(String) search} the tree prior to recalculation would return
     * garbage anyway.
     */
    @Redirect(method = "Lnet/minecraft/client/util/SearchTree;add(Ljava/lang/Object;)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/util/SearchTree;index(Ljava/lang/Object;)V"),
            allow = 1, require = 1)
    private void ppatches_optimizeSearchTree_add_ensureNotCalculated(SearchTree<T> instance, T element) {
        //no-op
    }
}
