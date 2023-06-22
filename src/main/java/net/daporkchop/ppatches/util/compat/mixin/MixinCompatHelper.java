package net.daporkchop.ppatches.util.compat.mixin;

import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.transformer.ext.Extensions;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * @author DaPorkchop_
 */
@UtilityClass
public class MixinCompatHelper {
    @SneakyThrows
    public static void forceSelectConfigs() {
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
    }
}
