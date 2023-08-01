package net.daporkchop.ppatches.modules.vanilla.useFieldsForSimpleConstantGetters.mixin;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

/**
 * @author DaPorkchop_
 */
@Mixin(Block.class)
abstract class MixinBlock {
    /**
     * @author DaPorkchop_
     * @reason replacing this method to make it more optimizable
     */
    @Overwrite
    public boolean isStickyBlock(IBlockState state) {
        return false;
    }
}
