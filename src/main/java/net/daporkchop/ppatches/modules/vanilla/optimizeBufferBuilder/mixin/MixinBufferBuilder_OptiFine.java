package net.daporkchop.ppatches.modules.vanilla.optimizeBufferBuilder.mixin;

import net.daporkchop.ppatches.modules.vanilla.optimizeBufferBuilder.OptimizedVertexFormatElement;
import net.daporkchop.ppatches.modules.vanilla.optimizeBufferBuilder.util.IMixinBufferBuilder_OptimizeBufferBuilder;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.vertex.VertexFormat;
import net.minecraft.client.renderer.vertex.VertexFormatElement;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * @author DaPorkchop_
 */
@Mixin(BufferBuilder.class)
abstract class MixinBufferBuilder_OptiFine {
    @Dynamic
    @Redirect(method = "endVertex()V",
            at = @At(value = "FIELD",
                    target = "Lnet/minecraft/client/renderer/BufferBuilder;vertexFormatIndex:I",
                    opcode = Opcodes.PUTFIELD),
            allow = 1, require = 1)
    private void ppatches_optimizeBufferBuilder_endVertex_dontSetVertexFormatIndex(BufferBuilder _this, int zero) {
        if (!OptimizedVertexFormatElement.ASSUME_VALID_VERTEX_FORMAT) { //reset the vertex format instead
            ((IMixinBufferBuilder_OptimizeBufferBuilder) this).ppatches_optimizeBufferBuilder_resetToFirstElement();
        }

        //no-op
    }

    @Dynamic
    @Redirect(method = "endVertex()V",
            at = @At(value = "FIELD",
                    target = "Lnet/minecraft/client/renderer/BufferBuilder;vertexFormatIndex:I",
                    opcode = Opcodes.GETFIELD),
            allow = 1, require = 1)
    private int ppatches_optimizeBufferBuilder_endVertex_dontGetVertexFormatIndex(BufferBuilder _this) {
        return 0;
    }

    @Dynamic
    @Redirect(method = "endVertex()V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/vertex/VertexFormat;getElement(I)Lnet/minecraft/client/renderer/vertex/VertexFormatElement;"),
            allow = 1, require = 1)
    private VertexFormatElement ppatches_optimizeBufferBuilder_endVertex_dontGetVertexFormatElement(VertexFormat format, int index) {
        return null;
    }

    @Dynamic
    @Redirect(method = "endVertex()V",
            at = @At(value = "FIELD",
                    target = "Lnet/minecraft/client/renderer/BufferBuilder;vertexFormatElement:Lnet/minecraft/client/renderer/vertex/VertexFormatElement;",
                    opcode = Opcodes.PUTFIELD),
            allow = 1, require = 1)
    private void ppatches_optimizeBufferBuilder_endVertex_dontSetVertexFormatElement(BufferBuilder _this, VertexFormatElement element) {
        //no-op
    }
}
