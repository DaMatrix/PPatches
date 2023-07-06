package net.daporkchop.ppatches.modules.vanilla.useFasterRandom.asm.fml.common;

import net.daporkchop.ppatches.modules.vanilla.useFasterRandom.FasterJavaRandom;
import net.minecraftforge.fml.common.registry.GameRegistry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

/**
 * @author DaPorkchop_
 */
@Mixin(value = GameRegistry.class, remap = false)
abstract class MixinGameRegistry {
    @Redirect(method = "Lnet/minecraftforge/fml/common/registry/GameRegistry;generateWorld(IILnet/minecraft/world/World;Lnet/minecraft/world/gen/IChunkGenerator;Lnet/minecraft/world/chunk/IChunkProvider;)V",
            at = @At(value = "NEW",
                    target = "(J)Ljava/util/Random;"),
            allow = 1, require = 1)
    private static Random ppatches_useFasterRandom_redirectNewRandomInstance(long seed) {
        return FasterJavaRandom.newInstance(seed);
    }
}
