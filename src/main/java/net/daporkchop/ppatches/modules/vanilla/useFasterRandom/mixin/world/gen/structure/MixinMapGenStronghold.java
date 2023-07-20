package net.daporkchop.ppatches.modules.vanilla.useFasterRandom.mixin.world.gen.structure;

import net.daporkchop.ppatches.modules.vanilla.useFasterRandom.FasterJavaRandom;
import net.minecraft.world.gen.structure.MapGenStronghold;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.Random;

/**
 * @author DaPorkchop_
 */
@Mixin(MapGenStronghold.class)
abstract class MixinMapGenStronghold {
    @Redirect(method = "Lnet/minecraft/world/gen/structure/MapGenStronghold;generatePositions()V",
            at = @At(value = "NEW",
                    target = "()Ljava/util/Random;"),
            allow = 1, require = 1)
    private Random ppatches_useFasterRandom_redirectNewRandomInstance() {
        return FasterJavaRandom.newInstance(); //we can't use ThreadLocalRandom here because setSeed() is called sometimes
    }
}
