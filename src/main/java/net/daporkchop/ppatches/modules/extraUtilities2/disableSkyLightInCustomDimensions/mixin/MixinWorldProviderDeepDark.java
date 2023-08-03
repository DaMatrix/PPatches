package net.daporkchop.ppatches.modules.extraUtilities2.disableSkyLightInCustomDimensions.mixin;

import net.daporkchop.ppatches.PPatchesMod;
import net.minecraft.world.WorldProvider;
import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Deep Dark dimension.
 * <p>
 * Now, technically {@code WorldProviderDeepDark} already overrides {@link WorldProvider#hasSkyLight()} to always return {@code false}, however we're going to set the
 * actual {@link WorldProvider#hasSkyLight} field to {@code false} and use a transformer to delete the override of {@link WorldProvider#hasSkyLight()} in order to
 * eliminate some dynamic dispatch.
 *
 * @author DaPorkchop_
 */
@Pseudo
@Mixin(targets = "com.rwtema.extrautils2.dimensions.deep_dark.WorldProviderDeepDark", remap = false)
abstract class MixinWorldProviderDeepDark extends MixinXUWorldProvider {
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
                    "Lcom/rwtema/extrautils2/dimensions/deep_dark/WorldProviderDeepDark;init()V",
                    "Lcom/rwtema/extrautils2/dimensions/deep_dark/WorldProviderDeepDark;func_76572_b()V", //mixin plugin can't automatically generate refmaps for this method, since it's a psuedo class
            },
            at = @At("RETURN"),
            allow = 1, require = 1)
    private void ppatches_disableSkyLightInCustomDimensions_init_disableSkyLight(CallbackInfo ci) {
        PPatchesMod.LOGGER.info("extraUtilities2.disableSkyLightInCustomDimensions: disabling sky light in dimension {}: \"{}\"",
                this.getDimensionType().getId(), this.getDimensionType().getName());
        this.hasSkyLight = false;
    }

    //TODO: figure out if i can get away with using @Delete here instead of a transformer
}
