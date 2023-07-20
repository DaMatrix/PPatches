package net.daporkchop.ppatches.modules.vanilla.fontRendererFixStyleResetShadow.mixin;

import net.minecraft.client.gui.FontRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Slice;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * When drawing text with a shadow, resets the formatting flags after drawing the shadow pass to prevent them from carrying over to the non-shadow pass.
 *
 * @author DaPorkchop_
 */
@Mixin(FontRenderer.class)
public abstract class MixinFontRenderer {
    @Shadow
    protected abstract void resetStyles();

    @Inject(method = "Lnet/minecraft/client/gui/FontRenderer;drawString(Ljava/lang/String;FFIZ)I",
            slice = @Slice(
                    from = @At(value = "CONSTANT:FIRST", args = "floatValue=1.0")),
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/FontRenderer;renderString(Ljava/lang/String;FFIZ)I",
                    ordinal = 0,
                    shift = At.Shift.AFTER),
            allow = 1, require = 1)
    private void ppatches_fontRendererFixStyleResetShadow_renderStringAtPos_resetStyleAfterShadowPass(CallbackInfoReturnable<Integer> ci) {
        this.resetStyles();
    }
}
