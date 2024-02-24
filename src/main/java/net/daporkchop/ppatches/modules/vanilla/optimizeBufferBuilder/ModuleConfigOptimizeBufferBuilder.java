package net.daporkchop.ppatches.modules.vanilla.optimizeBufferBuilder;

import net.daporkchop.ppatches.PPatchesConfig;
import net.minecraftforge.common.config.Config;

/**
 * @author DaPorkchop_
 */
public class ModuleConfigOptimizeBufferBuilder extends PPatchesConfig.ModuleConfigBase {
    @Config.Comment({
            "If true, assume that all vertex formats used with BufferBuilder are valid (i.e. they have at most one of each of the vertex elements supported by vanilla)"
            + " and that they are always used correctly (i.e. there is no code which calls BufferBuilder methods in the wrong order).",
            "This can increase BufferBuilder performance by an additional 30-40% on OpenJDK HotSpot JVMs. It's definitely safe to use for vanilla code, however some mods"
            + " may access the wrong BufferBuilder methods (either accidentally or on purpose) so this option is disabled by default.",
    })
    @Config.RequiresMcRestart
    public boolean assumeValidVertexFormat = false;

    public ModuleConfigOptimizeBufferBuilder(PPatchesConfig.ModuleState defaultState) {
        super(defaultState);
    }
}
