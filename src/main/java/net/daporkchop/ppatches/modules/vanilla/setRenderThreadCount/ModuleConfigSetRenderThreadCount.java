package net.daporkchop.ppatches.modules.vanilla.setRenderThreadCount;

import net.daporkchop.ppatches.PPatchesConfig;
import net.minecraftforge.common.config.Config;

/**
 * @author DaPorkchop_
 */
public class ModuleConfigSetRenderThreadCount extends PPatchesConfig.ModuleConfigBase {
    @Config.Comment({
            "The number of render threads to use.",
            "This value is not capped, but setting it too high will (obviously) cause the system to slow down significantly or run out of memory!",
    })
    @Config.RangeInt(min = 1)
    @Config.RequiresWorldRestart
    public int renderThreadCount = 6;

    @Config.Comment({
            "If false, chunk rendering will be done on a separate thread even if only a single render thread is being used.",
            "If true, the vanilla behavior will be kept - when only a single render thread is being used, all chunk rendering will be done on the client thread.",
    })
    @Config.RequiresWorldRestart
    public boolean useSeparateThreadIfSingleRenderThread = false;

    public ModuleConfigSetRenderThreadCount(PPatchesConfig.ModuleState defaultState) {
        super(defaultState);
    }
}
