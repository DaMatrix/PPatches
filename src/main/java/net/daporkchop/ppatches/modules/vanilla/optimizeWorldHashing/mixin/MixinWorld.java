package net.daporkchop.ppatches.modules.vanilla.optimizeWorldHashing.mixin;

import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.client.MinecraftForgeClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * For some reason, the default {@link Object#hashCode()} implementation is very slow, even though it just delegates to {@link System#identityHashCode(Object)}. This
 * causes very bad performance anywhere a {@link World} is used as a key in a map (such as the {@link MinecraftForgeClient#regionCache} used by
 * {@link MinecraftForgeClient#getRegionRenderCache(World, BlockPos)}, which is typically called once per tile entity per frame by TESR rendering code).
 * <p>
 * In a test world containing roughly 1000 OpenBlocks fans, this produced roughly a 50x speedup in {@link MinecraftForgeClient#getRegionRenderCache(World, BlockPos)}
 * (from ~76% of the total frame time to ~1.5%).
 *
 * @author DaPorkchop_
 */
@Mixin(World.class)
public abstract class MixinWorld {
    @Unique
    private final int ppatches_optimizeWorldHashing_cachedHashCode = System.identityHashCode(this);

    @Override
    public int hashCode() {
        return this.ppatches_optimizeWorldHashing_cachedHashCode;
    }
}
