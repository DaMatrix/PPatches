package net.daporkchop.ppatches.modules.vanilla.optimizeBufferBuilder.mixin;

import net.daporkchop.ppatches.util.mixin.ext.Delete;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.vertex.VertexFormatElement;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * @author DaPorkchop_
 */
@Mixin(BufferBuilder.class)
abstract class MixinBufferBuilder_RemoveVanillaFields {
    //we don't need these two fields, delete them to force a crash if any mod code tries to use them
    @Delete
    @Shadow
    private VertexFormatElement vertexFormatElement;

    @Delete
    @Shadow
    private int vertexFormatIndex;

    @Redirect(method = "reset()V",
            at = @At(value = "FIELD",
                    target = "Lnet/minecraft/client/renderer/BufferBuilder;vertexFormatElement:Lnet/minecraft/client/renderer/vertex/VertexFormatElement;",
                    opcode = Opcodes.PUTFIELD),
            allow = 1, require = 1)
    private void ppatches_optimizeBufferBuilder_reset_dontSetVertexFormatElement(BufferBuilder _this, VertexFormatElement element) {
        //no-op
    }

    @Redirect(method = "reset()V",
            at = @At(value = "FIELD",
                    target = "Lnet/minecraft/client/renderer/BufferBuilder;vertexFormatIndex:I",
                    opcode = Opcodes.PUTFIELD),
            allow = 1, require = 1)
    private void ppatches_optimizeBufferBuilder_reset_dontSetVertexFormatIndex(BufferBuilder _this, int index) {
        //no-op
    }

    @Redirect(method = "begin(ILnet/minecraft/client/renderer/vertex/VertexFormat;)V",
            at = @At(value = "FIELD",
                    target = "Lnet/minecraft/client/renderer/BufferBuilder;vertexFormatIndex:I",
                    opcode = Opcodes.GETFIELD),
            allow = 1, require = 1)
    private int ppatches_optimizeBufferBuilder_begin_dontGetVertexFormatIndex(BufferBuilder _this) {
        return 0;
    }

    @Redirect(method = "begin(ILnet/minecraft/client/renderer/vertex/VertexFormat;)V",
            at = @At(value = "FIELD",
                    target = "Lnet/minecraft/client/renderer/BufferBuilder;vertexFormatElement:Lnet/minecraft/client/renderer/vertex/VertexFormatElement;",
                    opcode = Opcodes.PUTFIELD),
            allow = 1, require = 1)
    private void ppatches_optimizeBufferBuilder_begin_dontSetVertexFormatElement(BufferBuilder _this, VertexFormatElement element) {
        //no-op
    }
}
