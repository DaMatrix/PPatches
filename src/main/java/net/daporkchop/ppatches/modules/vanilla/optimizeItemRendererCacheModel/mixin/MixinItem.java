package net.daporkchop.ppatches.modules.vanilla.optimizeItemRendererCacheModel.mixin;

import net.daporkchop.ppatches.modules.vanilla.optimizeItemRendererCacheModel.util.IMixinItem_OptimizeItemRendererCacheModel;
import net.minecraft.item.Item;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * @author DaPorkchop_
 */
@Mixin(Item.class)
abstract class MixinItem implements IMixinItem_OptimizeItemRendererCacheModel {
    @Unique
    private boolean ppatches_optimizeItemRendererCacheModel_excluded;

    @Override
    public final boolean ppatches_optimizeItemRendererCacheModel_excluded() {
        return this.ppatches_optimizeItemRendererCacheModel_excluded;
    }

    @Override
    public final void ppatches_optimizeItemRendererCacheModel_excluded(boolean value) {
        this.ppatches_optimizeItemRendererCacheModel_excluded = value;
    }
}
