package net.daporkchop.ppatches.modules.vanilla.useFieldsForSimpleConstantGetters.mixin;

import net.daporkchop.ppatches.util.mixin.ext.Delete;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.BiomeForest;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/**
 * @author DaPorkchop_
 */
@Mixin(BiomeForest.class)
abstract class MixinBiomeForest {
    /**
     * This deletes the override of {@link Biome#getBiomeClass()} in {@link BiomeForest}, which simply returns {@link BiomeForest BiomeForest.class} and is therefore rather useless.
     */
    @Delete
    @Shadow
    @SuppressWarnings("unused")
    public abstract Class<? extends Biome> getBiomeClass();
}
