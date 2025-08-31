package net.daporkchop.ppatches.modules.vanilla.optimizeRenderChunkMatrix.mixin;

import net.daporkchop.ppatches.util.mixin.ext.Delete;
import net.minecraft.client.renderer.GLAllocation;
import net.minecraft.client.renderer.chunk.RenderChunk;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.nio.FloatBuffer;

/**
 * @author DaPorkchop_
 */
@Mixin(RenderChunk.class)
abstract class MixinRenderChunk {
    @Unique
    private static FloatBuffer ppatches_optimizeRenderChunkMatrix_MODELVIEW_MATRIX;

    @Delete(removeInstanceInitializer = true)
    @Shadow
    @Final
    private FloatBuffer modelviewMatrix;

    @Redirect(method = "<init>",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/GLAllocation;createDirectFloatBuffer(I)Ljava/nio/FloatBuffer;"),
            allow = 1, require = 1)
    private FloatBuffer ppatches_optimizeRenderChunkMatrix_$init$_dontCreateModelViewMatrix(int capacity) {
        if (ppatches_optimizeRenderChunkMatrix_MODELVIEW_MATRIX == null) { //this is the first RenderChunk ever created, we should initialize the default ModelView matrix
            ppatches_optimizeRenderChunkMatrix_MODELVIEW_MATRIX = GLAllocation.createDirectFloatBuffer(capacity);
            this.initModelviewMatrix();
        }

        return null;
    }

    @Redirect(method = "initModelviewMatrix()V",
            at = @At(value = "FIELD",
                    target = "Lnet/minecraft/client/renderer/chunk/RenderChunk;modelviewMatrix:Ljava/nio/FloatBuffer;",
                    opcode = Opcodes.GETFIELD),
            allow = 1, require = 1)
    private FloatBuffer ppatches_optimizeRenderChunkMatrix_initModelViewMatrix_writeToStaticModelViewMatrix(RenderChunk _this) {
        return ppatches_optimizeRenderChunkMatrix_MODELVIEW_MATRIX;
    }

    @Redirect(method = "multModelviewMatrix()V",
            at = @At(value = "FIELD",
                    target = "Lnet/minecraft/client/renderer/chunk/RenderChunk;modelviewMatrix:Ljava/nio/FloatBuffer;",
                    opcode = Opcodes.GETFIELD),
            allow = 1, require = 1)
    private FloatBuffer ppatches_optimizeRenderChunkMatrix_multModelviewMatrix_useStaticModelViewMatrix(RenderChunk _this) {
        return ppatches_optimizeRenderChunkMatrix_MODELVIEW_MATRIX;
    }

    @Redirect(method = "setPosition(III)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/chunk/RenderChunk;initModelviewMatrix()V"),
            allow = 1, require = 0) //not required, CubicChunks also patches this
    private void ppatches_optimizeRenderChunkMatrix_setPosition_dontReinitializeModelViewMatrix(RenderChunk _this) {
        //no-op
    }

    @Shadow
    protected abstract void initModelviewMatrix();
}
