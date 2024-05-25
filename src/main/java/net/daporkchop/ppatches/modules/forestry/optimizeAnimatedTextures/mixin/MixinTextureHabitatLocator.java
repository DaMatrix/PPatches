package net.daporkchop.ppatches.modules.forestry.optimizeAnimatedTextures.mixin;

import net.daporkchop.ppatches.util.compat.optifine.OFCompatHelper;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * @author DaPorkchop_
 */
@Pseudo
@Mixin(targets = "forestry.apiculture.render.TextureHabitatLocator", remap = false)
abstract class MixinTextureHabitatLocator extends TextureAtlasSprite {
    protected MixinTextureHabitatLocator(String spriteName) {
        super(spriteName);
    }

    @Dynamic
    @Inject(method = "updateCompass(Lnet/minecraft/world/World;DDD)V",
            at = @At("HEAD"),
            cancellable = true,
            allow = 1, require = 1)
    private void ppatches_optimizeAnimatedTextures_updateCompass_respectOptiFineFastAnimations(CallbackInfo ci) {
        if (OFCompatHelper.OPTIFINE && this.animationMetadata == null) {
            ci.cancel();
        }
        OFCompatHelper.SmartAnimations_checkAnimationActiveForSpriteAndUpdateAnimationActive(this);
    }

    @Dynamic
    @Redirect(method = "updateCompass(Lnet/minecraft/world/World;DDD)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/texture/TextureUtil;uploadTextureMipmap([[IIIIIZZ)V", remap = true),
            allow = 1, require = 1)
    private void ppatches_optimizeAnimatedTextures_updateCompass_useVanillaCode(
            int[][] frameTextureData, int width, int height, int originX, int originY, boolean linearFiltering, boolean clamp) {
        if (OFCompatHelper.OPTIFINE && !OFCompatHelper.SmartAnimations_getAnimationActiveField(this)) {
            return;
        }

        int frameCount = this.animationMetadata.getFrameCount() == 0 ? this.framesTextureData.size() : this.animationMetadata.getFrameCount();

        //set frameCounter to the previous frame and tickCounter to the previous frame's time so that the vanilla animation update code will
        //actually switch to the requested frame
        int currentFrame = this.frameCounter;
        int previousFrame = currentFrame == 0 ? frameCount - 1 : currentFrame;
        this.tickCounter = this.animationMetadata.getFrameTimeSingle(previousFrame);
        this.frameCounter = previousFrame;

        super.updateAnimation();
    }
}
