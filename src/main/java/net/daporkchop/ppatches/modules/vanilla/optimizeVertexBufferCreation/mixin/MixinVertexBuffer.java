package net.daporkchop.ppatches.modules.vanilla.optimizeVertexBufferCreation.mixin;

import net.daporkchop.ppatches.modules.vanilla.optimizeVertexBufferCreation.OptimizeVertexBufferCreation;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.vertex.VertexBuffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * @author DaPorkchop_
 */
@Mixin(VertexBuffer.class)
abstract class MixinVertexBuffer {
    @Redirect(method = "<init>",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/OpenGlHelper;glGenBuffers()I"),
            allow = 1, require = 1)
    private int ppatches_optimizeVertexBufferCreation_$init$_onlyCallGenBuffersIfActiveBatch() {
        if (OptimizeVertexBufferCreation._PREALLOCATED_BUFFER_IDS != null) {
            int id = OptimizeVertexBufferCreation._PREALLOCATED_BUFFER_IDS.get();

            if (!OptimizeVertexBufferCreation._PREALLOCATED_BUFFER_IDS.hasRemaining()) { //there are no preallocated buffer ids left
                OptimizeVertexBufferCreation._PREALLOCATED_BUFFER_IDS = null;
            }
            return id;
        }

        return OpenGlHelper.glGenBuffers();
    }
}
