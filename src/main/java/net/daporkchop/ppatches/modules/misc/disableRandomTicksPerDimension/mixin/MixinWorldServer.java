package net.daporkchop.ppatches.modules.misc.disableRandomTicksPerDimension.mixin;

import net.daporkchop.ppatches.PPatchesConfig;
import net.daporkchop.ppatches.modules.misc.disableRandomTicksPerDimension.util.IMixinWorldServer_DisableRandomTicksPerDimension;
import net.minecraft.world.WorldProvider;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * @author DaPorkchop_
 */
@Mixin(WorldServer.class)
abstract class MixinWorldServer implements IMixinWorldServer_DisableRandomTicksPerDimension {
    private boolean ppatches_disableRandomTicksPerDimension_randomTicksDisabled;

    @Inject(method = "<init>",
            at = @At("RETURN"),
            allow = 1, require = 1)
    private void ppatches_disableRandomTicksPerDimension_$init$_precomputeRandomTicksDisabled(CallbackInfo ci) {
        this.ppatches_disableRandomTicksPerDimension_randomTicksDisabled = PPatchesConfig.misc_disableRandomTicksPerDimension.isBlacklisted((WorldServer) (Object) this);
    }

    @ModifyVariable(
            method = "Lnet/minecraft/world/WorldServer;updateBlocks()V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/WorldServer;isRaining()Z",
                    shift = At.Shift.BEFORE),
            ordinal = 0,
            allow = 1, require = 1)
    private int ppatches_disableRandomTicksPerDimension_updateBlocks_maybeSkipRandomTicks(int randomTickSpeed) {
        return this.ppatches_disableRandomTicksPerDimension_randomTicksDisabled ? 0 : randomTickSpeed;
    }

    @Redirect(method = "Lnet/minecraft/world/WorldServer;updateBlocks()V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/WorldProvider;canDoLightning(Lnet/minecraft/world/chunk/Chunk;)Z"),
            allow = 1, require = 1)
    private boolean ppatches_disableRandomTicksPerDimension_updateBlocks_maybeSkipLightning(WorldProvider provider, Chunk chunk) {
        return !this.ppatches_disableRandomTicksPerDimension_randomTicksDisabled && provider.canDoLightning(chunk);
    }

    @Redirect(method = "Lnet/minecraft/world/WorldServer;updateBlocks()V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/WorldProvider;canDoRainSnowIce(Lnet/minecraft/world/chunk/Chunk;)Z"),
            allow = 1, require = 1)
    private boolean ppatches_disableRandomTicksPerDimension_updateBlocks_maybeSkipRainSnowIce(WorldProvider provider, Chunk chunk) {
        return !this.ppatches_disableRandomTicksPerDimension_randomTicksDisabled && provider.canDoRainSnowIce(chunk);
    }

    @Override
    public final boolean ppatches_disableRandomTicksPerDimension_randomTicksDisabled() {
        return this.ppatches_disableRandomTicksPerDimension_randomTicksDisabled;
    }
}
