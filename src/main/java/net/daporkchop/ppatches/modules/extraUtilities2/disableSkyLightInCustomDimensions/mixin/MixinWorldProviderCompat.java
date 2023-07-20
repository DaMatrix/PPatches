package net.daporkchop.ppatches.modules.extraUtilities2.disableSkyLightInCustomDimensions.mixin;

import net.minecraft.world.WorldProvider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;

/**
 * @author DaPorkchop_
 */
@Pseudo
@Mixin(targets = "com.rwtema.extrautils2.compatibility.WorldProviderCompat", remap = false)
abstract class MixinWorldProviderCompat extends WorldProvider {
}
