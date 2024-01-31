package net.daporkchop.ppatches.modules.forge.optimizeGetPersistentChunks.util;

import com.google.common.cache.Cache;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.Multimap;
import net.minecraft.util.math.ChunkPos;
import net.minecraftforge.common.ForgeChunkManager;

/**
 * @author DaPorkchop_
 */
public interface IMixinWorld_OptimizeGetPersistentChunks {
    ImmutableSetMultimap<ChunkPos, ForgeChunkManager.Ticket> ppatches_optimizeGetPersistentChunks_forcedChunks();

    void ppatches_optimizeGetPersistentChunks_forcedChunks(ImmutableSetMultimap<ChunkPos, ForgeChunkManager.Ticket> forcedChunks);

    Multimap<String, ForgeChunkManager.Ticket> ppatches_optimizeGetPersistentChunks_tickets();

    void ppatches_optimizeGetPersistentChunks_tickets(Multimap<String, ForgeChunkManager.Ticket> tickets);

    Cache<Long, ?> ppatches_optimizeGetPersistentChunks_dormantChunkCache();

    void ppatches_optimizeGetPersistentChunks_dormantChunkCache(Cache<Long, ?> tickets);
}
