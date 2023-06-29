package net.daporkchop.ppatches.modules.mixin.optimizeCallbackInfoAllocation;

import net.daporkchop.ppatches.PPatchesConfig;
import net.minecraftforge.common.config.Config;

/**
 * @author DaPorkchop_
 */
public final class ModuleConfigOptimizeCallbackInfoAllocation extends PPatchesConfig.ModuleConfigBase {
    @Config.Comment({
            "If true, PPatches will transform Mixin callback methods which aren't private.",
            "This should be safe, however it may need to be disabled if a mod does something very unusual.",
    })
    @Config.RequiresMcRestart
    public boolean allowTransformingNonPrivateCallbacks = true;

    public ModuleConfigOptimizeCallbackInfoAllocation(PPatchesConfig.ModuleState defaultState) {
        super(defaultState);
    }
}
