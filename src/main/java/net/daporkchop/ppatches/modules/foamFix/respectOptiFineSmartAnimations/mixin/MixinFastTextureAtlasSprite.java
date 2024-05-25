package net.daporkchop.ppatches.modules.foamFix.respectOptiFineSmartAnimations.mixin;

import net.daporkchop.ppatches.util.compat.optifine.OFCompatHelper;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * @author DaPorkchop_
 */
@Pseudo
@Mixin(targets = "pl.asie.foamfix.client.FastTextureAtlasSprite", remap = false)
abstract class MixinFastTextureAtlasSprite extends TextureAtlasSprite {
    static {
        if (!OFCompatHelper.OPTIFINE) {
            throw new UnsupportedOperationException("PPatches: foamFix.respectOptiFineSmartAnimations requires OptiFine!");
        }
    }

    protected MixinFastTextureAtlasSprite(String spriteName) {
        super(spriteName);
    }

    @Dynamic
    @Inject(
            method = {
                    "updateAnimation()V",
                    "func_94219_l()V", //mixin plugin can't automatically generate refmaps for this method, since it's a psuedo class
            },
            at = @At("HEAD"),
            cancellable = true,
            allow = 1, require = 1)
    private void ppatches_respectOptiFineSmartAnimations_updateAnimation_checkAnimationActiveState(CallbackInfo ci) {
        // This doesn't quite match OptiFine behavior, as it won't tick the animation at all when disabled. However, I don't care :)
        if (this.animationMetadata == null || !OFCompatHelper.SmartAnimations_checkAnimationActiveForSpriteAndUpdateAnimationActive(this)) {
            ci.cancel();
        }
    }
}
