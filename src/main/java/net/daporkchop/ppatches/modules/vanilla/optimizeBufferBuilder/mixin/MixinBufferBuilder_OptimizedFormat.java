package net.daporkchop.ppatches.modules.vanilla.optimizeBufferBuilder.mixin;

import net.daporkchop.ppatches.modules.vanilla.optimizeBufferBuilder.OptimizedVertexFormat;
import net.daporkchop.ppatches.modules.vanilla.optimizeBufferBuilder.OptimizedVertexFormatElement;
import net.daporkchop.ppatches.modules.vanilla.optimizeBufferBuilder.util.IMixinVertexFormat_OptimizeBufferBuilder;
import net.daporkchop.ppatches.util.mixin.ext.AlwaysCancels;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.vertex.VertexFormat;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.nio.ByteBuffer;

/**
 * @author DaPorkchop_
 */
@Mixin(BufferBuilder.class)
abstract class MixinBufferBuilder_OptimizedFormat {
    @Shadow
    private ByteBuffer byteBuffer;
    @Shadow
    private int vertexCount;

    @Shadow
    private double xOffset;
    @Shadow
    private double yOffset;
    @Shadow
    private double zOffset;

    @Unique
    private OptimizedVertexFormat ppatches_optimizeBufferBuilder_optimizedVertexFormat;
    @Unique
    private OptimizedVertexFormatElement ppatches_optimizeBufferBuilder_optimizedVertexFormatElement;

    @Inject(method = "setVertexState(Lnet/minecraft/client/renderer/BufferBuilder$State;)V",
            at = @At(value = "FIELD",
                    target = "Lnet/minecraft/client/renderer/BufferBuilder;vertexFormat:Lnet/minecraft/client/renderer/vertex/VertexFormat;",
                    opcode = Opcodes.PUTFIELD,
                    shift = At.Shift.AFTER),
            allow = 1, require = 1)
    private void ppatches_optimizeBufferBuilder_setVertexState_nullOptimizedFields(CallbackInfo ci) {
        //i have no idea why the vanilla code clones the incoming VertexFormat here, we'll set the optimized fields to null to
        //  force a crash if some code tries to write into the BufferBuilder in this state
        this.ppatches_optimizeBufferBuilder_optimizedVertexFormat = null;
        this.ppatches_optimizeBufferBuilder_optimizedVertexFormatElement = null;
    }

    @Inject(method = "reset()V",
            at = @At("HEAD"),
            allow = 1, require = 1)
    private void ppatches_optimizeBufferBuilder_reset_nullOptimizedFields(CallbackInfo ci) {
        this.ppatches_optimizeBufferBuilder_optimizedVertexFormat = null;
        this.ppatches_optimizeBufferBuilder_optimizedVertexFormatElement = null;
    }

    @Inject(method = "begin(ILnet/minecraft/client/renderer/vertex/VertexFormat;)V",
            at = @At(value = "FIELD",
                    target = "Lnet/minecraft/client/renderer/BufferBuilder;vertexFormat:Lnet/minecraft/client/renderer/vertex/VertexFormat;",
                    opcode = Opcodes.PUTFIELD,
                    shift = At.Shift.AFTER),
            allow = 1, require = 1)
    private void ppatches_optimizeBufferBuilder_begin_acquireOptimizedFormat(int glMode, VertexFormat format, CallbackInfo ci) {
        this.ppatches_optimizeBufferBuilder_optimizedVertexFormat = ((IMixinVertexFormat_OptimizeBufferBuilder) format).ppatches_optimizeBufferBuilder_optimizedFormat();
        this.ppatches_optimizeBufferBuilder_optimizedVertexFormatElement = this.ppatches_optimizeBufferBuilder_optimizedVertexFormat.getFirstElement();
    }

    /**
     * @author DaPorkchop_
     * @reason we're replacing the entire method
     */
    @Overwrite
    private void nextVertexFormatIndex() {
        throw new UnsupportedOperationException("PPatches: vanilla.optimizedBufferBuilder has removed this method!");
    }

    //
    // ACTUAL FIELD UPDATERS
    //

    @Inject(method = "tex(DD)Lnet/minecraft/client/renderer/BufferBuilder;",
            at = @At(value = "FIELD",
                    target = "Lnet/minecraft/client/renderer/BufferBuilder;vertexCount:I",
                    opcode = Opcodes.GETFIELD,
                    shift = At.Shift.BEFORE),
            allow = 1, require = 1)
    private void ppatches_optimizeBufferBuilder_tex_dispatchOptimized(double u, double v, CallbackInfoReturnable<BufferBuilder> cir) {
        OptimizedVertexFormatElement next = this.ppatches_optimizeBufferBuilder_optimizedVertexFormatElement.tex(this.byteBuffer, this.vertexCount, u, v);
        if (!OptimizedVertexFormatElement.ASSUME_VALID_VERTEX_FORMAT) {
            this.ppatches_optimizeBufferBuilder_optimizedVertexFormatElement = next;
        }
    }

    @Inject(method = "lightmap(II)Lnet/minecraft/client/renderer/BufferBuilder;",
            at = @At(value = "FIELD",
                    target = "Lnet/minecraft/client/renderer/BufferBuilder;vertexCount:I",
                    opcode = Opcodes.GETFIELD,
                    shift = At.Shift.BEFORE),
            allow = 1, require = 1)
    private void ppatches_optimizeBufferBuilder_lightmap_dispatchOptimized(int skyLight, int blockLight, CallbackInfoReturnable<BufferBuilder> cir) {
        OptimizedVertexFormatElement next = this.ppatches_optimizeBufferBuilder_optimizedVertexFormatElement.lightmap(this.byteBuffer, this.vertexCount, skyLight, blockLight);
        if (!OptimizedVertexFormatElement.ASSUME_VALID_VERTEX_FORMAT) {
            this.ppatches_optimizeBufferBuilder_optimizedVertexFormatElement = next;
        }
    }

    @Inject(method = "color(IIII)Lnet/minecraft/client/renderer/BufferBuilder;",
            at = @At(value = "FIELD",
                    target = "Lnet/minecraft/client/renderer/BufferBuilder;vertexCount:I",
                    opcode = Opcodes.GETFIELD,
                    shift = At.Shift.BEFORE),
            allow = 1, require = 1)
    private void ppatches_optimizeBufferBuilder_color_dispatchOptimized(int r, int g, int b, int a, CallbackInfoReturnable<BufferBuilder> cir) {
        OptimizedVertexFormatElement next = this.ppatches_optimizeBufferBuilder_optimizedVertexFormatElement.color(this.byteBuffer, this.vertexCount, r, g, b, a);
        if (!OptimizedVertexFormatElement.ASSUME_VALID_VERTEX_FORMAT) {
            this.ppatches_optimizeBufferBuilder_optimizedVertexFormatElement = next;
        }
    }

    @Inject(method = "pos(DDD)Lnet/minecraft/client/renderer/BufferBuilder;",
            at = @At(value = "FIELD",
                    target = "Lnet/minecraft/client/renderer/BufferBuilder;vertexCount:I",
                    opcode = Opcodes.GETFIELD,
                    shift = At.Shift.BEFORE),
            allow = 1, require = 1)
    private void ppatches_optimizeBufferBuilder_pos_dispatchOptimized(double x, double y, double z, CallbackInfoReturnable<BufferBuilder> cir) {
        OptimizedVertexFormatElement next = this.ppatches_optimizeBufferBuilder_optimizedVertexFormatElement.pos(this.byteBuffer, this.vertexCount, x + this.xOffset, y + this.yOffset, z + this.zOffset);
        if (!OptimizedVertexFormatElement.ASSUME_VALID_VERTEX_FORMAT) {
            this.ppatches_optimizeBufferBuilder_optimizedVertexFormatElement = next;
        }
    }

    @Inject(method = "normal(FFF)Lnet/minecraft/client/renderer/BufferBuilder;",
            at = @At(value = "FIELD",
                    target = "Lnet/minecraft/client/renderer/BufferBuilder;vertexCount:I",
                    opcode = Opcodes.GETFIELD,
                    shift = At.Shift.BEFORE),
            allow = 1, require = 1)
    private void ppatches_optimizeBufferBuilder_normal_dispatchOptimized(float x, float y, float z, CallbackInfoReturnable<BufferBuilder> cir) {
        OptimizedVertexFormatElement next = this.ppatches_optimizeBufferBuilder_optimizedVertexFormatElement.normal(this.byteBuffer, this.vertexCount, x, y, z);
        if (!OptimizedVertexFormatElement.ASSUME_VALID_VERTEX_FORMAT) {
            this.ppatches_optimizeBufferBuilder_optimizedVertexFormatElement = next;
        }
    }

    //we cancel the CallbackInfo in a separate method, which seems to improve the chances of getting inlined
    @Inject(
            method = {
                    "tex(DD)Lnet/minecraft/client/renderer/BufferBuilder;",
                    "lightmap(II)Lnet/minecraft/client/renderer/BufferBuilder;",
                    "color(IIII)Lnet/minecraft/client/renderer/BufferBuilder;",
                    "pos(DDD)Lnet/minecraft/client/renderer/BufferBuilder;",
                    "normal(FFF)Lnet/minecraft/client/renderer/BufferBuilder;",
            },
            at = @At(value = "FIELD",
                    target = "Lnet/minecraft/client/renderer/BufferBuilder;vertexCount:I",
                    opcode = Opcodes.GETFIELD),
            cancellable = true,
            allow = 5, require = 5)
    @AlwaysCancels
    private void ppatches_optimizeBufferBuilder_returnEarlyAfterDispatch(CallbackInfoReturnable<BufferBuilder> cir) {
        cir.setReturnValue((BufferBuilder) (Object) this);
    }
}
