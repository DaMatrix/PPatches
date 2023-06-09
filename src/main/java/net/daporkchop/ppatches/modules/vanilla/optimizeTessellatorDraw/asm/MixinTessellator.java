package net.daporkchop.ppatches.modules.vanilla.optimizeTessellatorDraw.asm;

import net.daporkchop.ppatches.modules.vanilla.optimizeTessellatorDraw.VAOWorldVertexBufferUploader;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldVertexBufferUploader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * @author DaPorkchop_
 */
@Mixin(Tessellator.class)
public abstract class MixinTessellator {
    @Redirect(method = "<init>",
            at = @At(value = "NEW", target = "net/minecraft/client/renderer/WorldVertexBufferUploader"),
            allow = 1, require = 1)
    private WorldVertexBufferUploader ppatches_vaoRendering_useVaoBufferUploader() {
        return Tessellator.getInstance() == null
                ? new VAOWorldVertexBufferUploader() //this is the main Tessellator instance, create a new uploader
                : Tessellator.getInstance().vboUploader; //re-use the uploader instance from the main Tessellator
    }
}
