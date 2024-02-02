package net.daporkchop.ppatches.core.mixin;

import net.daporkchop.ppatches.PPatchesMod;
import net.daporkchop.ppatches.core.bootstrap.PPatchesBootstrap;
import net.minecraft.launchwrapper.LaunchClassLoader;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.ModClassLoader;
import net.minecraftforge.fml.common.ModContainer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.net.MalformedURLException;
import java.util.List;

/**
 * As documented <a href="https://github.com/SpongePowered/Mixin/issues/460>here</a>, this is a horrifically bad way of doing this. However, in spite of Mumfrey's
 * claims to the contrary, I haven't been able to find a better way of doing this, so here we go:
 *
 * @author DaPorkchop_
 */
@Mixin(value = Loader.class, priority = 2000)
public abstract class MixinLoader {
    @Shadow
    private List<ModContainer> mods;

    @Shadow
    private ModClassLoader modClassLoader;

    @Inject(method = "Lnet/minecraftforge/fml/common/Loader;loadMods(Ljava/util/List;)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraftforge/fml/common/LoadController;transition(Lnet/minecraftforge/fml/common/LoaderState;Z)V",
                    ordinal = 1),
            remap = false,
            allow = 1, require = 1)
    private void ppatches_core_forciblyLoadMixinsIntoNonCoremodClasses(List<String> injectedModContainers, CallbackInfo ci) {
        PPatchesMod.LOGGER.info("Loading mod mixins...");

        for (ModContainer mod : this.mods) {
            try {
                if (this.modClassLoader.getParent() instanceof LaunchClassLoader
                    && ((LaunchClassLoader) this.modClassLoader.getParent()).getSources().contains(mod.getSource().toURI().toURL())) {
                    continue;
                }

                this.modClassLoader.addFile(mod.getSource());
            } catch (MalformedURLException e) {
                throw new RuntimeException(e);
            }
        }

        PPatchesBootstrap.modsOnClasspath();
    }
}
