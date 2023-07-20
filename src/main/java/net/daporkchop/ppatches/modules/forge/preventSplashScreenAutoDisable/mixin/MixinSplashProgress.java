package net.daporkchop.ppatches.modules.forge.preventSplashScreenAutoDisable.mixin;

import net.daporkchop.ppatches.PPatchesMod;
import net.minecraftforge.fml.client.SplashProgress;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * @author DaPorkchop_
 */
@Mixin(value = SplashProgress.class, remap = false)
abstract class MixinSplashProgress {
    @Inject(method = "Lnet/minecraftforge/fml/client/SplashProgress;disableSplash()Z",
            at = @At("HEAD"),
            cancellable = true)
    private static void ppatches_preventSplashScreenAutoDisable_disableSplash_disableDisableSplash(CallbackInfoReturnable<Boolean> ci) {
        PPatchesMod.LOGGER.info("Preventing the Forge splash screen from being automatically disabled");
        ci.setReturnValue(false);
    }
}
