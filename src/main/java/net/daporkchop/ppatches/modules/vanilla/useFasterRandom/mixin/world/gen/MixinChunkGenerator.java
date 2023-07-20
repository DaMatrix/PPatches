package net.daporkchop.ppatches.modules.vanilla.useFasterRandom.mixin.world.gen;

import net.daporkchop.ppatches.modules.vanilla.useFasterRandom.FasterJavaRandom;
import net.minecraft.world.gen.ChunkGeneratorEnd;
import net.minecraft.world.gen.ChunkGeneratorFlat;
import net.minecraft.world.gen.ChunkGeneratorHell;
import net.minecraft.world.gen.ChunkGeneratorOverworld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.Random;

/**
 * @author DaPorkchop_
 */
@Mixin({
        ChunkGeneratorEnd.class,
        ChunkGeneratorFlat.class,
        ChunkGeneratorHell.class,
        ChunkGeneratorOverworld.class,
})
abstract class MixinChunkGenerator {
    @Redirect(method = "<init>",
            at = @At(value = "NEW",
                    target = "(J)Ljava/util/Random;"),
            allow = 1, require = 1)
    private Random ppatches_useFasterRandom_redirectNewRandomInstance(long seed) {
        return FasterJavaRandom.newInstance(seed);
    }
}
