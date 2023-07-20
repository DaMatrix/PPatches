package net.daporkchop.ppatches.modules.foamFix.optimizeTextureInterpolation.mixin;

import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Pseudo;

/**
 * @author DaPorkchop_
 */
@Pseudo
@Mixin(targets = "pl.asie.foamfix.client.FastTextureAtlasSprite", remap = false)
public abstract class MixinFastTextureAtlasSprite {
    /**
     * @reason use a more optimized implementation of texture interpolation which only uses integer math
     * @author DaPorkchop_
     */
    @Dynamic
    @Overwrite
    private boolean interpolateFrame(int[] to, int[] from1, int[] from2, double ratioIn) {
        if (from1.length != from2.length) {
            return false;
        } else {
            int ratio = (int) (ratioIn * 255.0d);
            int invRatio = 255 - ratio;

            assert ratio >= 0 && ratio <= 255 : ratio;

            for (int i = 0; i < from1.length; i++) {
                int color1 = from1[i];
                int color2 = from2[i];

                int rb = ((ratio * (color1 & 0x00FF00FF) + invRatio * (color2 & 0x00FF00FF)) >>> 8) & 0x00FF00FF;
                int g_ = ((ratio * (color1 & 0x0000FF00) + invRatio * (color2 & 0x0000FF00)) >>> 8) & 0x0000FF00;

                /*int rb = (ratio * (color1 & 0x00FF00FF) + invRatio * (color2 & 0x00FF00FF));
                int g_ = (ratio * (color1 & 0x0000FF00) + invRatio * (color2 & 0x0000FF00));

                //divide by 255
                rb = ((rb + 0x00010001 + ((rb >>> 8) & 0x00FF00FF)) >>> 8) & 0x00FF00FF;
                g_ = ((g_ + 0x00000100 + ((g_ >>> 8) & 0x0000FF00)) >>> 8) & 0x0000FF00;*/

                //the original code uses the alpha value from color1 without blending
                to[i] = (color1 & 0xFF000000) | rb | g_;
            }
            return true;
        }
    }
}
