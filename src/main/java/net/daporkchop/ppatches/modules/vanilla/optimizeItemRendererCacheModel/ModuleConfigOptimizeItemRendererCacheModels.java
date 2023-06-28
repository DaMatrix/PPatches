package net.daporkchop.ppatches.modules.vanilla.optimizeItemRendererCacheModel;

import net.daporkchop.ppatches.PPatchesConfig;
import net.daporkchop.ppatches.modules.vanilla.optimizeItemRendererCacheModel.util.IMixinItem_OptimizeItemRendererCacheModel;
import net.minecraft.item.Item;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.config.Config;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * @author DaPorkchop_
 */
public final class ModuleConfigOptimizeItemRendererCacheModels extends PPatchesConfig.ModuleConfigBase {
    @Config.Comment({
            "Determines which items should be excluded from this optimization.",
            "Supported syntax is '<modid>:*' to target all items with a given modid, or '<modid>:<registry_name>.",
    })
    @Config.RequiresMcRestart //TODO: we can avoid requiring a minecraft restart if we reset the caches in RenderItem somehow
    public String[] exclude = {
            "mwc:*",
    };

    @SideOnly(Side.CLIENT)
    private transient boolean needsUpdate;

    public ModuleConfigOptimizeItemRendererCacheModels(PPatchesConfig.ModuleState defaultState) {
        super(defaultState);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void loadFromConfig(Configuration configuration, String category, boolean init) {
        super.loadFromConfig(configuration, category, init);
        this.needsUpdate = true;
    }

    @SideOnly(Side.CLIENT)
    public boolean shouldOptimizeItem(Item item) {
        if (this.needsUpdate) { //update code is in separate method to help JIT
            this.update();
        }
        return !((IMixinItem_OptimizeItemRendererCacheModel) item).ppatches_optimizeItemRendererCacheModel_excluded();
    }

    @SideOnly(Side.CLIENT)
    private void update() {
        this.needsUpdate = false;

        //un-exclude all items
        for (Item item : Item.REGISTRY) {
            ((IMixinItem_OptimizeItemRendererCacheModel) item).ppatches_optimizeItemRendererCacheModel_excluded(false);
        }

        //set the excluded flag to true for each excluded item
        //  this is not a super efficient algorithm (we could do this whole method in a single pass in O(n) time using a pair of hash tables), but for now i can't be bothered
        for (String exclusion : this.exclude) {
            if (exclusion.endsWith(":*")) { //exclude all items with the given modid
                String modid = exclusion.substring(0, exclusion.length() - ":*".length());
                for (Item item : Item.REGISTRY) {
                    if (modid.equals(item.getRegistryName().getNamespace())) {
                        ((IMixinItem_OptimizeItemRendererCacheModel) item).ppatches_optimizeItemRendererCacheModel_excluded(true);
                    }
                }
            } else { //exclude the item with the given id
                Item item = Item.REGISTRY.getObject(new ResourceLocation(exclusion));
                if (item != null) {
                    ((IMixinItem_OptimizeItemRendererCacheModel) item).ppatches_optimizeItemRendererCacheModel_excluded(true);
                }
            }
        }
    }
}
