package net.daporkchop.ppatches.modules.vanilla.useFieldsForSimpleConstantGetters.mixin;

import net.minecraft.block.BlockBreakable;
import net.minecraft.block.BlockSlime;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import org.spongepowered.asm.mixin.Mixin;

/**
 * @author DaPorkchop_
 */
@Mixin(BlockSlime.class)
abstract class MixinBlockSlime extends BlockBreakable {
    protected MixinBlockSlime(Material materialIn, boolean ignoreSimilarityIn) {
        super(materialIn, ignoreSimilarityIn);
    }

    @Override
    public boolean isStickyBlock(IBlockState state) {
        return true;
    }
}
