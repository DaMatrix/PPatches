package net.daporkchop.ppatches.modules.vanilla.optimizeItemRendererCacheModel.asm;

import net.daporkchop.ppatches.modules.vanilla.optimizeItemRendererCacheModel.util.IMixinItem_OptimizeItemRendererCacheModel;
import net.minecraft.item.Item;
import org.spongepowered.asm.mixin.Mixin;

/**
 * @author DaPorkchop_
 */
@Mixin(Item.class)
abstract class MixinItem implements IMixinItem_OptimizeItemRendererCacheModel {
    public boolean ppatches_optimizeItemRendererCacheModel_excluded;

    @Override
    public boolean ppatches_optimizeItemRendererCacheModel_excluded() {
        return this.ppatches_optimizeItemRendererCacheModel_excluded;
    }

    @Override
    public void ppatches_optimizeItemRendererCacheModel_excluded(boolean value) {
        this.ppatches_optimizeItemRendererCacheModel_excluded = value;
    }
}
