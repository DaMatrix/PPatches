package net.daporkchop.ppatches.core.asm;

import net.daporkchop.ppatches.PPatchesLoadingPlugin;
import net.minecraft.launchwrapper.LaunchClassLoader;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.ModClassLoader;
import net.minecraftforge.fml.common.ModContainer;
import org.apache.logging.log4j.LogManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.transformer.ext.Extensions;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.util.List;

/**
 * As documented <a href="https://github.com/SpongePowered/Mixin/issues/460>here</a>, this is a horrifically bad way of doing this. However, in spite of Mumfrey's
 * claims to the contrary, I haven't been able to find a better way of doing this, so here we go:
 *
 * @author DaPorkchop_
 */
@Mixin(Loader.class)
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
        LogManager.getLogger("PPatches").info("Loading mod mixins...");

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

        PPatchesLoadingPlugin.loadModules(MixinEnvironment.Phase.DEFAULT);

        try {
            // This will very likely break on the next major mixin release.
            Class<?> proxyClass = Class.forName("org.spongepowered.asm.mixin.transformer.Proxy");
            Field transformerField = proxyClass.getDeclaredField("transformer");
            transformerField.setAccessible(true);
            Object transformer = transformerField.get(null);

            Class<?> mixinTransformerClass = Class.forName("org.spongepowered.asm.mixin.transformer.MixinTransformer");

            // Mixin 0.7.11
            try {
                Method selectConfigsMethod = mixinTransformerClass.getDeclaredMethod("selectConfigs", MixinEnvironment.class);
                selectConfigsMethod.setAccessible(true);
                selectConfigsMethod.invoke(transformer, MixinEnvironment.getCurrentEnvironment());

                Method prepareConfigs = mixinTransformerClass.getDeclaredMethod("prepareConfigs", MixinEnvironment.class);
                prepareConfigs.setAccessible(true);
                prepareConfigs.invoke(transformer, MixinEnvironment.getCurrentEnvironment());
                return;
            } catch (NoSuchMethodException ex) {
                // no-op
            }

            Field processorField = mixinTransformerClass.getDeclaredField("processor");
            processorField.setAccessible(true);
            Object processor = processorField.get(transformer);

            Class<?> mixinProcessorClass = Class.forName("org.spongepowered.asm.mixin.transformer.MixinProcessor");

            Field extensionsField = mixinProcessorClass.getDeclaredField("extensions");
            extensionsField.setAccessible(true);
            Object extensions = extensionsField.get(processor);

            Method selectConfigsMethod = mixinProcessorClass.getDeclaredMethod("selectConfigs", MixinEnvironment.class);
            selectConfigsMethod.setAccessible(true);
            selectConfigsMethod.invoke(processor, MixinEnvironment.getCurrentEnvironment());

            // Mixin 0.8.4+
            try {
                Method prepareConfigs = mixinProcessorClass.getDeclaredMethod("prepareConfigs", MixinEnvironment.class, Extensions.class);
                prepareConfigs.setAccessible(true);
                prepareConfigs.invoke(processor, MixinEnvironment.getCurrentEnvironment(), extensions);
                return;
            } catch (NoSuchMethodException ex) {
                // no-op
            }

            // Mixin 0.8+
            try {
                Method prepareConfigs = mixinProcessorClass.getDeclaredMethod("prepareConfigs", MixinEnvironment.class);
                prepareConfigs.setAccessible(true);
                prepareConfigs.invoke(processor, MixinEnvironment.getCurrentEnvironment());
                return;
            } catch (NoSuchMethodException ex) {
                // no-op
            }

            throw new UnsupportedOperationException("Unsupported Mixin");
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }
}
