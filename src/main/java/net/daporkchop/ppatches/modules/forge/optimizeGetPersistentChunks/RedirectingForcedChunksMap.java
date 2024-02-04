package net.daporkchop.ppatches.modules.forge.optimizeGetPersistentChunks;

import com.google.common.collect.ImmutableSetMultimap;
import net.daporkchop.ppatches.modules.forge.optimizeGetPersistentChunks.util.IMixinWorld_OptimizeGetPersistentChunks;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraftforge.common.ForgeChunkManager;

import java.util.AbstractMap;
import java.util.Set;

/**
 * @author DaPorkchop_
 */
public final class RedirectingForcedChunksMap extends AbstractMap<World, ImmutableSetMultimap<ChunkPos, ForgeChunkManager.Ticket>> {
    @Override
    public Set<Entry<World, ImmutableSetMultimap<ChunkPos, ForgeChunkManager.Ticket>>> entrySet() {
        throw new UnsupportedOperationException();
    }

    @Override
    public ImmutableSetMultimap<ChunkPos, ForgeChunkManager.Ticket> get(Object key) {
        return ((IMixinWorld_OptimizeGetPersistentChunks) key).ppatches_optimizeGetPersistentChunks_forcedChunks();
    }

    @Override
    public ImmutableSetMultimap<ChunkPos, ForgeChunkManager.Ticket> put(World key, ImmutableSetMultimap<ChunkPos, ForgeChunkManager.Ticket> value) {
        ImmutableSetMultimap<ChunkPos, ForgeChunkManager.Ticket> old = ((IMixinWorld_OptimizeGetPersistentChunks) key).ppatches_optimizeGetPersistentChunks_forcedChunks();
        ((IMixinWorld_OptimizeGetPersistentChunks) key).ppatches_optimizeGetPersistentChunks_forcedChunks(value);
        return old;
    }

    @Override
    public ImmutableSetMultimap<ChunkPos, ForgeChunkManager.Ticket> remove(Object key) {
        ImmutableSetMultimap<ChunkPos, ForgeChunkManager.Ticket> old = ((IMixinWorld_OptimizeGetPersistentChunks) key).ppatches_optimizeGetPersistentChunks_forcedChunks();
        ((IMixinWorld_OptimizeGetPersistentChunks) key).ppatches_optimizeGetPersistentChunks_forcedChunks(null);
        return old;
    }
}
