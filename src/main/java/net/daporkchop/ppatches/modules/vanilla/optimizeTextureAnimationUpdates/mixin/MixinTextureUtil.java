package net.daporkchop.ppatches.modules.vanilla.optimizeTextureAnimationUpdates.mixin;

import net.minecraft.client.renderer.texture.TextureUtil;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/**
 * @author DaPorkchop_
 */
@Mixin(TextureUtil.class)
abstract class MixinTextureUtil {
    @ModifyConstant(method = "allocateTextureImpl(IIII)V",
            constant = @Constant(intValue = GL11.GL_RGBA),
            allow = 1, require = 1)
    private static int ppatches_optimizeTextureAnimationUpdates_allocateTextureImpl_useRGBA8Texture(int GL_RGBA) {
        return GL11.GL_RGBA8;
    }
}
