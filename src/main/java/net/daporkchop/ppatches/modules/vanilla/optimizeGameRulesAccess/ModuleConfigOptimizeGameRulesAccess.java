package net.daporkchop.ppatches.modules.vanilla.optimizeGameRulesAccess;

import net.daporkchop.ppatches.PPatchesConfig;
import net.daporkchop.ppatches.util.COWArrayUtils;
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
    })
    @Config.RequiresMcRestart
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

    public synchronized void encounteredUnknownModdedGameRule(String name) {
        if (!this.addModdedGameRulesAutomatically //don't add modded game rules which we don't know about
                || Arrays.binarySearch(this.moddedGameRules, name) >= 0) { //the modded game rule is already in the config
            return;
        }

        String[] newModdedGameRules = COWArrayUtils.append(this.moddedGameRules, name);
        Arrays.sort(newModdedGameRules);
        this.moddedGameRules = newModdedGameRules;

        PPatchesConfig.save();
    }
}
