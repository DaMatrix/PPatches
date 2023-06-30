package net.daporkchop.ppatches.modules.vanilla.optimizeSearchTree;

import net.daporkchop.ppatches.PPatchesConfig;
import net.minecraftforge.common.config.Config;

/**
 * @author DaPorkchop_
 */
public final class ModuleConfigOptimizeSearchTree extends PPatchesConfig.ModuleConfigBase {
    @Config.Comment({
            "If true, SearchTree recalculation will be done asynchronously.",
            "This should be safe, however it may need to be disabled if a mod does something very unusual.",
    })
    @Config.RequiresMcRestart
    public boolean asynchronousSearchTreeRecalculation = true;

    public ModuleConfigOptimizeSearchTree(PPatchesConfig.ModuleState defaultState) {
        super(defaultState);
    }
}
