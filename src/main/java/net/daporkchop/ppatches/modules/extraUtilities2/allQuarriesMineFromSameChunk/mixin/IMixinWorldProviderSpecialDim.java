package net.daporkchop.ppatches.modules.extraUtilities2.allQuarriesMineFromSameChunk.mixin;

import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.biome.Biome;
import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * @author DaPorkchop_
 */
@Pseudo
@Mixin(targets = "com.rwtema.extrautils2.dimensions.workhousedim.WorldProviderSpecialDim", remap = false)
public interface IMixinWorldProviderSpecialDim {
    @Dynamic
    @Invoker
    static ChunkPos callAdjustChunkRef(ChunkPos pos) {
        throw new AssertionError();
    }

    @Dynamic
    @Invoker
    static ChunkPos callPrepareNewChunk(Biome targetBiome) {
        throw new AssertionError();
    }

    @Dynamic
    @Invoker
    static void callReleaseChunk(ChunkPos posKey) {
        throw new AssertionError();
    }
}
