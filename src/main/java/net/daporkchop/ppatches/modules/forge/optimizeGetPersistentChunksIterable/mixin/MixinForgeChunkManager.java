package net.daporkchop.ppatches.modules.forge.optimizeGetPersistentChunksIterable.mixin;

import com.google.common.collect.ImmutableSetMultimap;
import net.daporkchop.ppatches.modules.forge.optimizeGetPersistentChunksIterable.FastForcedChunksMergingChunkIterator;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.common.ForgeChunkManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

import java.util.Iterator;

/**
 * @author DaPorkchop_
 */
@Mixin(value = ForgeChunkManager.class, remap = false)
abstract class MixinForgeChunkManager {
    /**
     * @author DaPorkchop_
     * @reason we're completely replacing the method
     */
    @Overwrite
    public static Iterator<Chunk> getPersistentChunksIterableFor(World world, Iterator<Chunk> chunkIterator) {
        ImmutableSetMultimap<ChunkPos, ForgeChunkManager.Ticket> persistentChunks = ForgeChunkManager.getPersistentChunksFor(world);
        Iterator<ChunkPos> forcedChunkPosIterator = persistentChunks.keySet().iterator();

        //this iterator is not very efficient, but it's still faster than
        // putting all the chunks into a set every tick :)
        return new FastForcedChunksMergingChunkIterator(forcedChunkPosIterator, chunkIterator, persistentChunks, world);
    }
}
