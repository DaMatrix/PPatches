package net.daporkchop.ppatches.modules.vanilla.optimizeTextureUtilHeapAllocations.asm;

import net.minecraft.client.renderer.texture.TextureUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

import java.awt.image.BufferedImage;

/**
 * @author DaPorkchop_
 */
@Mixin(TextureUtil.class)
abstract class MixinTextureUtil {
    @ModifyConstant(method = "Lnet/minecraft/client/renderer/texture/TextureUtil;uploadTextureImageSubImpl(Ljava/awt/image/BufferedImage;IIZZ)V",
            constant = @Constant(intValue = 4194304),
            allow = 1, require = 1)
    private static int ppatches_optimizeTextureUtilHeapAllocations_reduceBlockSizeIfSmallImage(int dataBufferSize, BufferedImage img, int dstX, int dstY, boolean blurred, boolean clamped) {
        return Math.min(img.getHeight() * img.getWidth(), dataBufferSize);
    }
}
