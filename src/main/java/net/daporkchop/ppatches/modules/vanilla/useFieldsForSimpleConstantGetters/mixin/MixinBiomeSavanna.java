package net.daporkchop.ppatches.modules.vanilla.useFieldsForSimpleConstantGetters.mixin;

import net.daporkchop.ppatches.util.mixin.ext.Delete;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.BiomeSavanna;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/**
 * @author DaPorkchop_
 */
@Mixin(BiomeSavanna.class)
abstract class MixinBiomeSavanna {
    /**
     * This deletes the override of {@link Biome#getBiomeClass()} in {@link BiomeSavanna}, which simply returns {@link BiomeSavanna BiomeSavanna.class} and is therefore rather useless.
     */
    @Delete
    @Shadow
    @SuppressWarnings("unused")
    public abstract Class<? extends Biome> getBiomeClass();
}
