package net.daporkchop.ppatches.modules.forge.optimizeGetPersistentChunks.mixin;

import com.google.common.cache.Cache;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.Multimap;
import net.daporkchop.ppatches.modules.forge.optimizeGetPersistentChunks.util.IMixinWorld_OptimizeGetPersistentChunks;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraftforge.common.ForgeChunkManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * @author DaPorkchop_
 */
@Mixin(World.class)
abstract class MixinWorld implements IMixinWorld_OptimizeGetPersistentChunks {
    @Unique
    private ImmutableSetMultimap<ChunkPos, ForgeChunkManager.Ticket> ppatches_optimizeGetPersistentChunks_forcedChunks;
    @Unique
    private Multimap<String, ForgeChunkManager.Ticket> ppatches_optimizeGetPersistentChunks_tickets;
    @Unique
    private Cache<Long, ?> ppatches_optimizeGetPersistentChunks_dormantChunkCache;

    @Override
    public final ImmutableSetMultimap<ChunkPos, ForgeChunkManager.Ticket> ppatches_optimizeGetPersistentChunks_forcedChunks() {
        return this.ppatches_optimizeGetPersistentChunks_forcedChunks;
    }

    @Override
    public final void ppatches_optimizeGetPersistentChunks_forcedChunks(ImmutableSetMultimap<ChunkPos, ForgeChunkManager.Ticket> forcedChunks) {
        this.ppatches_optimizeGetPersistentChunks_forcedChunks = forcedChunks;
    }

    @Override
    public final Multimap<String, ForgeChunkManager.Ticket> ppatches_optimizeGetPersistentChunks_tickets() {
        return this.ppatches_optimizeGetPersistentChunks_tickets;
    }

    @Override
    public final void ppatches_optimizeGetPersistentChunks_tickets(Multimap<String, ForgeChunkManager.Ticket> tickets) {
        this.ppatches_optimizeGetPersistentChunks_tickets = tickets;
    }

    @Override
    public final Cache<Long, ?> ppatches_optimizeGetPersistentChunks_dormantChunkCache() {
        return this.ppatches_optimizeGetPersistentChunks_dormantChunkCache;
    }

    @Override
    public final void ppatches_optimizeGetPersistentChunks_dormantChunkCache(Cache<Long, ?> tickets) {
        this.ppatches_optimizeGetPersistentChunks_dormantChunkCache = tickets;
    }
}
