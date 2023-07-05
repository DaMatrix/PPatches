package net.daporkchop.ppatches.modules.vanilla.useFasterRandom.asm.common;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

/**
 * @author DaPorkchop_
 */
@Mixin({
        net.minecraft.command.CommandHelp.class,
        net.minecraft.server.MinecraftServer.class,
        net.minecraft.world.Explosion.class,
        //net.minecraft.world.gen.NoiseGeneratorImproved.class,
        //net.minecraft.world.gen.NoiseGeneratorSimplex.class,
})
abstract class Mixin_UseThreadLocalRandomInCtor_NoSeed {
    @Redirect(method = "<init>*",
            at = @At(value = "NEW",
                    target = "()Ljava/util/Random;"),
            allow = 1, require = 1)
    private Random ppatches_useFasterRandom_redirectNewRandomInstance() {
        return ThreadLocalRandom.current(); //this is safe to use here because setSeed() is never called
    }
}
