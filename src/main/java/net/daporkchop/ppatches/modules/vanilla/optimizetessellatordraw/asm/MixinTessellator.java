package net.daporkchop.ppatches.modules.vanilla.optimizetessellatordraw.asm;

import net.daporkchop.ppatches.modules.vanilla.optimizetessellatordraw.VAOWorldVertexBufferUploader;
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
        return new VAOWorldVertexBufferUploader();
    }

    /*@Unique
    private VAOWorldVertexBufferUploader ppatches_vaoRendering_vaoUploader;

    @Inject(method = "<init>",
            at = @At("RETURN"),
            allow = 1, require = 1)
    private void ppatches_vaoRendering_init(CallbackInfo ci) {
        this.ppatches_vaoRendering_vaoUploader = new VAOWorldVertexBufferUploader();
    }

    @Redirect(method = "draw()V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/WorldVertexBufferUploader;draw(Lnet/minecraft/client/renderer/BufferBuilder;)V"),
            allow = 1, require = 1)
    private void ppatches_vaoRendering_draw(WorldVertexBufferUploader uploader, BufferBuilder builder) {
        //uploader.draw(builder);
        this.ppatches_vaoRendering_vaoUploader.draw(builder);
    }*/
}
