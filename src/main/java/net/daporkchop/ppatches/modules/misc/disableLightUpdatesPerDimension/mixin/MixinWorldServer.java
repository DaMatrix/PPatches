package net.daporkchop.ppatches.modules.misc.disableLightUpdatesPerDimension.mixin;

import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * @author DaPorkchop_
 */
@Mixin(WorldServer.class)
abstract class MixinWorldServer extends MixinWorld {
    @Inject(method = "Lnet/minecraft/world/WorldServer;playerCheckLight()V",
            at = @At("HEAD"),
            cancellable = true,
            allow = 1, require = 1)
    private void ppatches_disableLightUpdatesPerDimension_playerCheckLight_skipIfDisabled(CallbackInfo ci) {
        if (this.ppatches_disableLightUpdatesPerDimension_lightUpdatesDisabled()) {
            ci.cancel();
        }
    }

    @Redirect(method = "Lnet/minecraft/world/WorldServer;updateBlocks()V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/chunk/Chunk;enqueueRelightChecks()V"),
            allow = 1, require = 1)
    private void ppatches_disableLightUpdatesPerDimension_updateBlocks_maybeSkipEnqueueChunkRelightChecks(Chunk chunk) {
        if (!this.ppatches_disableLightUpdatesPerDimension_lightUpdatesDisabled()) {
            chunk.enqueueRelightChecks();
        }
    }
}
