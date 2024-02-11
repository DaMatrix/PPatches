package net.daporkchop.ppatches.modules.extraUtilities2.allQuarriesMineFromSameChunk.util;

import net.daporkchop.ppatches.modules.extraUtilities2.allQuarriesMineFromSameChunk.QuarryGroup;
import net.minecraft.world.biome.Biome;

/**
 * @author DaPorkchop_
 */
public interface IMixinMinecraftServer_AllQuarriesMineFromSameChunk {
    QuarryGroup ppatches_allQuarriesMineFromSameChunk_quarryGroupFor(Biome targetBiome);
}
