package net.daporkchop.ppatches.modules.extraUtilities2.disableSkyLightInCustomDimensions.asm;

import net.daporkchop.ppatches.PPatchesMod;
import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Quantum Quarry dimension.
 *
 * @author DaPorkchop_
 */
@Pseudo
@Mixin(targets = "com.rwtema.extrautils2.dimensions.workhousedim.WorldProviderSpecialDim", remap = false)
abstract class MixinWorldProviderSpecialDim extends MixinXUWorldProvider {
    /**
     * This method serves as a dummy injection point; it will be silently discarded if another mixin targeting the same class adds the same override.
     */
    @Unique
    @Override
    public void init() {
        super.init();
    }

    @Dynamic
    @Inject(
            method = {
                    "Lcom/rwtema/extrautils2/dimensions/workhousedim/WorldProviderSpecialDim;init()V",
                    "Lcom/rwtema/extrautils2/dimensions/workhousedim/WorldProviderSpecialDim;func_76572_b()V", //mixin plugin can't automatically generate refmaps for this method, since it's a psuedo class
            },
            at = @At("RETURN"),
            allow = 1, require = 1)
    private void ppatches_disableSkyLightInCustomDimensions_init_disableSkyLight(CallbackInfo ci) {
        PPatchesMod.LOGGER.info("extraUtilities2.disableSkyLightInCustomDimensions: disabling sky light in dimension {}: \"{}\"",
                this.getDimensionType().getId(), this.getDimensionType().getName());
        this.hasSkyLight = false;
    }
}
