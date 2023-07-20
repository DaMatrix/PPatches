package net.daporkchop.ppatches.modules.extraUtilities2.disableSkyLightInCustomDimensions.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;

/**
 * @author DaPorkchop_
 */
@Pseudo
@Mixin(targets = "com.rwtema.extrautils2.dimensions.XUWorldProvider", remap = false)
abstract class MixinXUWorldProvider extends MixinWorldProviderCompat {
}
