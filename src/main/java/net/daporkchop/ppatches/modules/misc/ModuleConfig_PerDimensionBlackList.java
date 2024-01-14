package net.daporkchop.ppatches.modules.misc;

import net.daporkchop.ppatches.PPatchesConfig;
import net.minecraft.world.World;
import net.minecraftforge.common.config.Config;

/**
 * @author DaPorkchop_
 */
public class ModuleConfig_PerDimensionBlackList extends PPatchesConfig.ModuleConfigBase {
    @Config.Comment({
            "The dimension IDs which should be excluded.",
    })
    @Config.RequiresWorldRestart
    public int[] blacklistDimensions = {
            -9999, //extra utilities quarry dimension
    };

    public ModuleConfig_PerDimensionBlackList(PPatchesConfig.ModuleState defaultState) {
        super(defaultState);
    }

    public boolean isBlacklisted(World world) {
        for (int dim : this.blacklistDimensions) {
            if (dim == world.provider.getDimension()) {
                return true;
            }
        }
        return false;
    }
}
