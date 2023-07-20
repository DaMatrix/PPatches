package net.daporkchop.ppatches.modules.vanilla.useFasterRandom.mixin.world.chunk;

import net.daporkchop.ppatches.modules.vanilla.useFasterRandom.FasterJavaRandom;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.EmptyChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.Random;

/**
 * @author DaPorkchop_
 */
@Mixin({Chunk.class, EmptyChunk.class})
abstract class MixinChunk {
    @Redirect(method = "getRandomWithSeed(J)Ljava/util/Random;",
            at = @At(value = "NEW", target = "(J)Ljava/util/Random;"),
            allow = 1, require = 1)
    private Random ppatches_useFasterRandom_redirectNewRandomInstance(long seed) {
        return FasterJavaRandom.newInstance(seed); //we can't use ThreadLocalRandom here because setSeed() is called sometimes
    }
}
