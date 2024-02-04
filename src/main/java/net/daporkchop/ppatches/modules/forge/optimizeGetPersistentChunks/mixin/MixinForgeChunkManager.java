package net.daporkchop.ppatches.modules.forge.optimizeGetPersistentChunks.mixin;

import com.google.common.collect.ImmutableSetMultimap;
import net.daporkchop.ppatches.modules.forge.optimizeGetPersistentChunks.RedirectingForcedChunksMap;
import net.daporkchop.ppatches.util.mixin.ext.MakeFinal;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraftforge.common.ForgeChunkManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.Map;

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
        return new RedirectingForcedChunksMap();
    }
}
