package net.daporkchop.ppatches.modules.journeymap.skiprenderingoffscreentooltips.asm;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.awt.geom.Point2D;

/**
 * JourneyMap renders tooltips for every draw step with a non-null {@code titlePosition}. It seems {@code titlePosition} is set when a draw step is
 * moused over, and set to {@code null} when the mouse leaves again. However, {@code titlePosition} is non-null when a draw step is first constructed,
 * meaning that until moused over for the first time, every draw step will be rendering its tooltip, normally off-screen. If lots of draw steps are
 * present (such as with FTB Utilities' chunk claiming functionality), this can result in a huge amount of time being spent rendering text which isn't
 * visible in the first place.
 * <p>
 * In a test world with the minimap set to size 30, and all visible chunks claimed using FTB Utilities, this produced roughly a 3.5x speedup in JourneyMap
 * map rendering (from ~60% of the total frame time to ~15%).
 *
 * @author DaPorkchop_
 */
@Pseudo
@Mixin(targets = "journeymap.client.render.draw.BaseOverlayDrawStep", remap = false)
public abstract class MixinBaseOverlayDrawStep {
    @Shadow
    protected Point2D.Double titlePosition;

    @Inject(method = "<init>",
            at = @At("RETURN"),
            allow = 1, require = 1)
    private void ppatches_skipRenderingOffscreenTooltips_setTitlePositionToNull(CallbackInfo ci) {
        this.titlePosition = null;
    }
}
