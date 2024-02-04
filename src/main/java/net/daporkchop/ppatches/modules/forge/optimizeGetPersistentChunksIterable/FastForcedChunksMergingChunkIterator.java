package net.daporkchop.ppatches.modules.forge.optimizeGetPersistentChunksIterable;

import com.google.common.collect.ImmutableSetMultimap;
import lombok.RequiredArgsConstructor;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.common.ForgeChunkManager;

import java.util.Iterator;

/**
 * @author DaPorkchop_
 */
@RequiredArgsConstructor
public final class FastForcedChunksMergingChunkIterator implements Iterator<Chunk> {
    private final Iterator<ChunkPos> forcedChunkPosIterator;
    private final Iterator<Chunk> chunkIterator;
    private final ImmutableSetMultimap<ChunkPos, ForgeChunkManager.Ticket> persistentChunks;
    private final World world;

    private boolean usingForcedChunks = true;
    private Chunk nextChunk;

    @Override
    public boolean hasNext() {
        if (this.usingForcedChunks) {
            if (this.forcedChunkPosIterator.hasNext()) {
                return true;
            }
            this.usingForcedChunks = false;
        }
        while (this.chunkIterator.hasNext()) {
            Chunk nextChunk = this.chunkIterator.next();
            if (!this.persistentChunks.containsKey(nextChunk.getPos())) {
                this.nextChunk = nextChunk;
                return true;
            }
        }
        return false;
    }

    @Override
    public Chunk next() {
        if (this.usingForcedChunks) {
            ChunkPos nextPos = this.forcedChunkPosIterator.next();
            return this.world.getChunk(nextPos.x, nextPos.z);
        }
        return this.nextChunk;
    }
}
