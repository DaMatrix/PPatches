package net.daporkchop.ppatches.modules.forge.optimizeChunkProviderServerUnloading.mixin;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSetMultimap;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.ChunkProviderServer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

import java.util.Iterator;

/**
 * @author DaPorkchop_
 */
@Mixin(ChunkProviderServer.class)
abstract class MixinChunkProviderServer {
    @Shadow
    @Final
    public WorldServer world;

    @Inject(
            method = "Lnet/minecraft/world/gen/ChunkProviderServer;queueUnload(Lnet/minecraft/world/chunk/Chunk;)V",
            at = @At("HEAD"),
            cancellable = true,
            allow = 1, require = 1)
    private void ppatches_optimizeChunkProviderServerChunkUnloading_queueUnload_dontAddForcedChunksToUnloadQueue(Chunk chunk, CallbackInfo ci) {
        if (this.world.getPersistentChunks().containsKey(chunk.getPos())) {
            //if this is a forced chunk, we shouldn't bother adding it to the unload queue at all
            ci.cancel();
        }
    }

    @Redirect(
            method = "Lnet/minecraft/world/gen/ChunkProviderServer;tick()Z",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/WorldServer;getPersistentChunks()Lcom/google/common/collect/ImmutableSetMultimap;",
                    remap = false),
            allow = 1, require = 1)
    private ImmutableSetMultimap<?, ?> ppatches_optimizeChunkProviderServerChunkUnloading_tick_dontIterateOverPersistentChunks(WorldServer world) {
        return ImmutableSetMultimap.of();
    }

    @Inject(
            method = "Lnet/minecraft/world/gen/ChunkProviderServer;tick()Z",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/chunk/Chunk;onUnload()V",
                    shift = At.Shift.BEFORE),
            locals = LocalCapture.CAPTURE_FAILHARD,
            allow = 1, require = 1)
    private void ppatches_optimizeChunkProviderServerChunkUnloading_tick_ensureWeDontUnloadForcedChunks(CallbackInfoReturnable<Boolean> ci, Iterator<Chunk> iterator, int i, Long olong, Chunk chunk) {
        Preconditions.checkState(!this.world.getPersistentChunks().containsKey(chunk.getPos()),
                "attempted to unload forced chunk (%d,%d), did another mod add it to the droppedChunks set directly???", chunk.x, chunk.z);
    }
}
