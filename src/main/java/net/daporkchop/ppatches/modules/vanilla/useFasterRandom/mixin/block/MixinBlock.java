package net.daporkchop.ppatches.modules.vanilla.useFasterRandom.mixin.block;

import net.daporkchop.ppatches.util.mixin.ext.MakeFinal;
import net.minecraft.block.Block;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import java.util.Random;

/**
 * @author DaPorkchop_
 */
@Mixin(Block.class)
abstract class MixinBlock {
    //we want this field to be final to help optimization
    @MakeFinal
    @Shadow(remap = false)
    protected static Random RANDOM;
}
