package net.daporkchop.ppatches.modules.vanilla.optimizeVertexBufferCreation.mixin;

import net.daporkchop.ppatches.modules.vanilla.optimizeVertexBufferCreation.OptimizeVertexBufferCreation;
import net.daporkchop.ppatches.util.compat.optifine.OFCompatHelper;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.ViewFrustum;
import net.minecraft.client.renderer.chunk.IRenderChunkFactory;
import net.minecraft.util.BlockRenderLayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * @author DaPorkchop_
 */
@Mixin(ViewFrustum.class)
abstract class MixinViewFrustum {
    @Shadow
    protected int countChunksX;
    @Shadow
    protected int countChunksY;
    @Shadow
    protected int countChunksZ;

    @Inject(method = "createRenderChunks(Lnet/minecraft/client/renderer/chunk/IRenderChunkFactory;)V",
            at = @At("HEAD"),
            allow = 1, require = 1)
    private void ppatches_optimizeVertexBufferCreation_createRenderChunks_preallocateVertexBuffers(IRenderChunkFactory renderChunkFactory, CallbackInfo ci) {
        if (OpenGlHelper.useVbo()) {
            //TODO: we should preallocate more if OptiFine render regions are enabled
            int renderChunkCount = this.countChunksX * this.countChunksY * this.countChunksZ;

            if (OFCompatHelper.OPTIFINE && OFCompatHelper.Config_isRenderRegions()) {
                //OptiFine adds some additional vertex buffers when render regions are enabled. since every render region occupies 16x16 chunks, we'll
                //approximate the total number of additional vertex buffers per axis as ceilDiv(countChunksX)
                renderChunkCount += this.countChunksY
                        * (((this.countChunksX - 1) >> 4) + 1)
                        * (((this.countChunksZ - 1) >> 4) + 1);
            }

            OptimizeVertexBufferCreation.preAllocateBuffers(renderChunkCount * BlockRenderLayer.values().length);
        }
    }
}
