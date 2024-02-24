package net.daporkchop.ppatches.modules.vanilla.optimizeBufferBuilder.mixin;

import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.vertex.VertexFormat;
import net.minecraft.util.math.MathHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;

/**
 * @author DaPorkchop_
 */
@Mixin(BufferBuilder.class)
abstract class MixinBufferBuilder_GrowBuffer {
    @Shadow
    private IntBuffer rawIntBuffer;

    @Shadow
    private int vertexCount;

    @Shadow
    private VertexFormat vertexFormat;

    @Shadow
    private ByteBuffer byteBuffer;

    @Shadow
    protected abstract void growBuffer(int increaseAmount);

    //redirect all calls to growBuffer() to check if we need to grow the buffer BEFORE calling the function containing all of the buffer growing logic.
    //  this should allow all code which uses ByteBuffer to be optimized better since much less bytecode will be inlined.
    @Redirect(method = "*",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/BufferBuilder;growBuffer(I)V"),
            allow = 4, require = 4)
    private void ppatches_optimizeBufferBuilder_skipGrowBufferTestQuickly(BufferBuilder _this, int increaseAmount) {
        //TODO: this calculation could probably be optimized more
        if ((MathHelper.roundUp(increaseAmount, 4) >> 2) > this.rawIntBuffer.remaining() || this.vertexCount * this.vertexFormat.getSize() + increaseAmount > this.byteBuffer.capacity()) {
            this.growBuffer(increaseAmount);
        }
    }
}
