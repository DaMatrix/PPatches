package net.daporkchop.ppatches.modules.extraUtilities2.allQuarriesMineFromSameChunk.util;

import net.daporkchop.ppatches.modules.extraUtilities2.allQuarriesMineFromSameChunk.QuarryGroup;

/**
 * @author DaPorkchop_
 */
public interface IMixinTileQuarry_AllQuarriesMineFromSameChunk {
    QuarryGroup ppatches_allQuarriesMineFromSameChunk_cmpxchgGroup(QuarryGroup expect, QuarryGroup update);
}
