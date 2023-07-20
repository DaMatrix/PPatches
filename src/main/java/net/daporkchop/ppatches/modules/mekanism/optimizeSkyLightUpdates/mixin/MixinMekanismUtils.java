package net.daporkchop.ppatches.modules.mekanism.optimizeSkyLightUpdates.mixin;

import net.minecraft.util.math.BlockPos;
import net.minecraft.world.EnumSkyBlock;
import net.minecraft.world.World;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Slice;

/**
 * @author DaPorkchop_
 */
@Pseudo
@Mixin(targets = "mekanism.common.util.MekanismUtils", remap = false)
abstract class MixinMekanismUtils {
    @Dynamic
    @Redirect(method = "Lmekanism/common/util/MekanismUtils;updateAllLightTypes(Lnet/minecraft/world/World;Lnet/minecraft/util/math/BlockPos;)V",
            slice = @Slice(
                    from = @At(value = "FIELD",
                            opcode = Opcodes.GETSTATIC,
                            target = "Lnet/minecraft/world/EnumSkyBlock;SKY:Lnet/minecraft/world/EnumSkyBlock;", remap = true)),
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/World;checkLightFor(Lnet/minecraft/world/EnumSkyBlock;Lnet/minecraft/util/math/BlockPos;)Z", remap = true),
            allow = 1, require = 1)
    private static boolean ppatches_optimizeSkyLightUpdates_onlyUpdateSkyLightIfNecessary(World world, EnumSkyBlock lightType, BlockPos pos) {
        return world.provider.hasSkyLight() && world.checkLightFor(lightType, pos);
    }
}
