package net.daporkchop.ppatches.modules.vanilla.optimizeGameRulesAccess;

import net.daporkchop.ppatches.PPatchesConfig;
import net.minecraftforge.common.config.Config;
import net.minecraftforge.common.config.Configuration;

import java.util.Arrays;

/**
 * @author DaPorkchop_
 */
public class ModuleConfigOptimizeGameRulesAccess extends PPatchesConfig.ModuleConfigBase {
    public transient String[] effectiveModdedGameRules;

    @Config.Comment({
            "A list of additional non-vanilla game rules which should be optimized by this transformer.",
    })
    @Config.RequiresMcRestart
    public String[] moddedGameRules = {};

    @Config.Comment({
            "If true, any non-vanilla game rules will be automatically added to nonVanillaRules when they are initially registered, and will be optimized the next time the game starts.",
            "This option currently does nothing.",
    })
    @Config.RequiresMcRestart
    //TODO: implement (we'll need to implement proper config saving)
    public boolean addModdedGameRulesAutomatically = true;

    public ModuleConfigOptimizeGameRulesAccess(PPatchesConfig.ModuleState defaultState) {
        super(defaultState);
    }

    @Override
    public void loadFromConfig(Configuration configuration, String category, boolean init) {
        super.loadFromConfig(configuration, category, init);
        Arrays.sort(this.moddedGameRules);
        this.effectiveModdedGameRules = this.moddedGameRules;
    }
}
