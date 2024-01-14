package net.daporkchop.ppatches.modules.misc.disableLightUpdatesPerDimension.mixin;

import net.daporkchop.ppatches.modules.misc.disableLightUpdatesPerDimension.util.IMixinWorld_DisableLightUpdatesPerDimension;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * @author DaPorkchop_
 */
@Mixin(Chunk.class)
abstract class MixinChunk {
    @Shadow
    @Final
    private World world;

    @Redirect(method = "Lnet/minecraft/world/chunk/Chunk;setBlockState(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/state/IBlockState;)Lnet/minecraft/block/state/IBlockState;",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/block/state/IBlockState;getLightOpacity(Lnet/minecraft/world/IBlockAccess;Lnet/minecraft/util/math/BlockPos;)I"),
            allow = 2, require = 2)
    private int ppatches_disableLightUpdatesPerDimension_setBlockState_skipGetLightOpacityIfDisabled(IBlockState state, IBlockAccess world, BlockPos pos) {
        return ((IMixinWorld_DisableLightUpdatesPerDimension) this.world).ppatches_disableLightUpdatesPerDimension_lightUpdatesDisabled() ? -1 : state.getLightOpacity(world, pos);
    }

    @Inject(method = "Lnet/minecraft/world/chunk/Chunk;relightBlock(III)V",
            at = @At("HEAD"),
            cancellable = true,
            allow = 1, require = 1)
    private void ppatches_disableLightUpdatesPerDimension_relightBlock_skipIfDisabled(CallbackInfo ci) {
        if (((IMixinWorld_DisableLightUpdatesPerDimension) this.world).ppatches_disableLightUpdatesPerDimension_lightUpdatesDisabled()) {
            ci.cancel();
        }
    }

    @Inject(method = "Lnet/minecraft/world/chunk/Chunk;enqueueRelightChecks()V",
            at = @At("HEAD"),
            cancellable = true,
            allow = 1, require = 1)
    private void ppatches_disableLightUpdatesPerDimension_enqueueRelightChecks_skipIfDisabled(CallbackInfo ci) {
        if (((IMixinWorld_DisableLightUpdatesPerDimension) this.world).ppatches_disableLightUpdatesPerDimension_lightUpdatesDisabled()) {
            ci.cancel();
        }
    }

    @Inject(method = "Lnet/minecraft/world/chunk/Chunk;checkLight()V",
            at = @At(value = "FIELD",
                    target = "Lnet/minecraft/world/chunk/Chunk;isLightPopulated:Z",
                    opcode = Opcodes.PUTFIELD,
                    ordinal = 0,
                    shift = At.Shift.AFTER),
            cancellable = true,
            allow = 1, require = 1)
    private void ppatches_disableLightUpdatesPerDimension_checkLight_skipIfDisabled(CallbackInfo ci) {
        if (((IMixinWorld_DisableLightUpdatesPerDimension) this.world).ppatches_disableLightUpdatesPerDimension_lightUpdatesDisabled()) {
            ci.cancel();
        }
    }
}
