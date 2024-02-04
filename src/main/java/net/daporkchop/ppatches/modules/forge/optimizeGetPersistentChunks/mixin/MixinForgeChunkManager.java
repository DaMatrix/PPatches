package net.daporkchop.ppatches.modules.forge.optimizeGetPersistentChunks.mixin;

import com.google.common.collect.ImmutableSetMultimap;
import net.daporkchop.ppatches.modules.forge.optimizeGetPersistentChunks.util.IMixinWorld_OptimizeGetPersistentChunks;
import net.daporkchop.ppatches.util.mixin.ext.MakeFinal;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraftforge.common.ForgeChunkManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.AbstractMap;
import java.util.Map;
import java.util.Set;

/**
 * @author DaPorkchop_
 */
@Mixin(value = ForgeChunkManager.class, remap = false)
abstract class MixinForgeChunkManager {
    //we want this field to be final to help optimization
    @MakeFinal
    @Shadow
    private static Map<World, ImmutableSetMultimap<ChunkPos, ForgeChunkManager.Ticket>> forcedChunks;

    @Redirect(method = "<clinit>",
            at = @At(value = "INVOKE",
                    target = "Ljava/util/Collections;synchronizedMap(Ljava/util/Map;)Ljava/util/Map;"),
            allow = 1, require = 1)
    private static Map<World, ImmutableSetMultimap<ChunkPos, ForgeChunkManager.Ticket>> ppatches_optimizeGetPersistentChunks_$clinit$_initializeForcedChunksMapToRedirectingVersion(Map<?, ?> map) {
        return new AbstractMap<World, ImmutableSetMultimap<ChunkPos, ForgeChunkManager.Ticket>>() {
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
        };
    }
}
