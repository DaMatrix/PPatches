package net.daporkchop.ppatches.modules.misc.disableLightUpdatesPerDimension.mixin;

import net.daporkchop.ppatches.PPatchesConfig;
import net.daporkchop.ppatches.modules.misc.disableLightUpdatesPerDimension.util.IMixinWorld_DisableLightUpdatesPerDimension;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * @author DaPorkchop_
 */
@Mixin(World.class)
abstract class MixinWorld implements IMixinWorld_DisableLightUpdatesPerDimension {
    @Unique
    private boolean ppatches_disableLightUpdatesPerDimension_lightUpdatesDisabled;

    @Override
    public final boolean ppatches_disableLightUpdatesPerDimension_lightUpdatesDisabled() {
        return this.ppatches_disableLightUpdatesPerDimension_lightUpdatesDisabled;
    }

    @Inject(method = "<init>",
            at = @At("RETURN"),
            allow = 1, require = 1)
    private void ppatches_disableLightUpdatesPerDimension_$init$_precomputeLightUpdatesDisabled(CallbackInfo ci) {
        this.ppatches_disableLightUpdatesPerDimension_lightUpdatesDisabled = PPatchesConfig.misc_disableLightUpdatesPerDimension.isBlacklisted((World) (Object) this);
    }

    @Redirect(method = "Lnet/minecraft/world/World;setBlockState(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/state/IBlockState;I)Z",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/block/state/IBlockState;getLightValue(Lnet/minecraft/world/IBlockAccess;Lnet/minecraft/util/math/BlockPos;)I"),
            allow = 2, require = 2)
    private int ppatches_disableLightUpdatesPerDimension_setBlockState_getLightValueIfEnabled(IBlockState state, IBlockAccess world, BlockPos pos) {
        return this.ppatches_disableLightUpdatesPerDimension_lightUpdatesDisabled ? -1 : state.getLightValue(world, pos);
    }

    @Redirect(method = "Lnet/minecraft/world/World;setBlockState(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/state/IBlockState;I)Z",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/block/state/IBlockState;getLightOpacity(Lnet/minecraft/world/IBlockAccess;Lnet/minecraft/util/math/BlockPos;)I"),
            allow = 2, require = 2)
    private int ppatches_disableLightUpdatesPerDimension_setBlockState_getLightOpacityIfEnabled(IBlockState state, IBlockAccess world, BlockPos pos) {
        return this.ppatches_disableLightUpdatesPerDimension_lightUpdatesDisabled ? -1 : state.getLightOpacity(world, pos);
    }
}
