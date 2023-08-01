package net.daporkchop.ppatches.modules.vanilla.optimizeSearchTree;

import net.daporkchop.ppatches.PPatchesConfig;
import net.daporkchop.ppatches.util.mixin.MixinConfigPluginAdapter;

/**
 * @author DaPorkchop_
 */
public class OptimizeSearchTreeMixinConfigPlugin extends MixinConfigPluginAdapter {
    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (mixinClassName.endsWith("_AsyncCalculation")) {
            return PPatchesConfig.vanilla_optimizeSearchTree.asynchronousSearchTreeRecalculation;
        }
        return super.shouldApplyMixin(targetClassName, mixinClassName);
    }
}
