package net.daporkchop.ppatches.modules.misc.disableRandomTicksPerDimension.mixin;

import net.daporkchop.ppatches.modules.misc.disableRandomTicksPerDimension.util.IMixinWorldServer_DisableRandomTicksPerDimension;
import net.minecraft.world.WorldProvider;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * @author DaPorkchop_
 */
@Mixin(value = WorldServer.class, priority = 1001) //higher priority to allow injecting into methods merged by a CubicChunks mixin
abstract class MixinWorldServer_CubicChunks {
    @Dynamic
    @Inject(
            method = "Lnet/minecraft/world/WorldServer;tickCube(ILio/github/opencubicchunks/cubicchunks/api/world/ICube;J)V", remap = false,
            at = @At("HEAD"),
            cancellable = true,
            allow = 1, require = 1)
    private void ppatches_disableRandomTicksPerDimension_CubicChunks_tickCube_maybeSkipRandomTicks(CallbackInfo ci) {
        if (((IMixinWorldServer_DisableRandomTicksPerDimension) this).ppatches_disableRandomTicksPerDimension_randomTicksDisabled()) {
            ci.cancel();
        }
    }

    @Dynamic
    @Redirect(method = "Lnet/minecraft/world/WorldServer;tickColumn(ZZLnet/minecraft/world/chunk/Chunk;)V", remap = false,
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/WorldProvider;canDoLightning(Lnet/minecraft/world/chunk/Chunk;)Z"),
            allow = 1, require = 1)
    private boolean ppatches_disableRandomTicksPerDimension_CubicChunks_tickColumn_maybeSkipLightning(WorldProvider provider, Chunk chunk) {
        return !((IMixinWorldServer_DisableRandomTicksPerDimension) this).ppatches_disableRandomTicksPerDimension_randomTicksDisabled() && provider.canDoLightning(chunk);
    }

    @Dynamic
    @Redirect(method = "Lnet/minecraft/world/WorldServer;tickColumn(ZZLnet/minecraft/world/chunk/Chunk;)V", remap = false,
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/WorldProvider;canDoRainSnowIce(Lnet/minecraft/world/chunk/Chunk;)Z"),
            allow = 1, require = 1)
    private boolean ppatches_disableRandomTicksPerDimension_CubicChunks_tickColumn_maybeSkipRainSnowIce(WorldProvider provider, Chunk chunk) {
        return !((IMixinWorldServer_DisableRandomTicksPerDimension) this).ppatches_disableRandomTicksPerDimension_randomTicksDisabled() && provider.canDoRainSnowIce(chunk);
    }
}
