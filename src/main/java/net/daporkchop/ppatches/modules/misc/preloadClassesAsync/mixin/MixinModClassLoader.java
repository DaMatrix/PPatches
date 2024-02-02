package net.daporkchop.ppatches.modules.misc.preloadClassesAsync.mixin;

import lombok.SneakyThrows;
import net.daporkchop.ppatches.modules.misc.preloadClassesAsync.PreloadClassesDummyTransformer;
import net.minecraftforge.fml.common.ModClassLoader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.nio.charset.StandardCharsets;

/**
 * @author DaPorkchop_
 */
@Mixin(value = ModClassLoader.class, remap = false)
abstract class MixinModClassLoader extends ClassLoader {
    @Inject(method = "loadClass",
            at = @At("HEAD"),
            allow = 1, require = 1)
    @SneakyThrows
    private void ppatches_preloadClassesAsync_loadClass_preLoadClass(String name, CallbackInfoReturnable<Class<?>> ci) {
        PreloadClassesDummyTransformer.INSTANCE.loadedClassesOutput.write((name + System.lineSeparator()).getBytes(StandardCharsets.UTF_8));
    }
}
