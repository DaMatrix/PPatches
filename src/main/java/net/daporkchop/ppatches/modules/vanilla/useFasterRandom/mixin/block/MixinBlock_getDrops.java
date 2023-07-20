package net.daporkchop.ppatches.modules.vanilla.useFasterRandom.mixin.block;

import net.minecraft.block.BlockCrops;
import net.minecraft.block.BlockLeaves;
import net.minecraft.block.BlockNetherWart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

/**
 * @author DaPorkchop_
 */
@Mixin({ BlockCrops.class, BlockLeaves.class, BlockNetherWart.class })
abstract class MixinBlock_getDrops {
    @Redirect(method = "getDrops(Lnet/minecraft/util/NonNullList;Lnet/minecraft/world/IBlockAccess;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/state/IBlockState;I)V",
            at = @At(value = "NEW",
                    target = "()Ljava/util/Random;"),
            allow = 1, require = 1)
    private Random ppatches_useFasterRandom_redirectNewRandomInstance() {
        return ThreadLocalRandom.current();
    }
}
