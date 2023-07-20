package net.daporkchop.ppatches.modules.vanilla.useFasterRandom.mixin.common;

import net.daporkchop.ppatches.modules.vanilla.useFasterRandom.FasterJavaRandom;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.Random;

/**
 * @author DaPorkchop_
 */
@Mixin({
        net.minecraft.entity.Entity.class,
        net.minecraft.inventory.ContainerEnchantment.class,
        net.minecraft.world.gen.MapGenBase.class,
        net.minecraft.world.World.class,
})
abstract class Mixin_UseFasterJavaRandomInCtor_NoSeed {
    @Redirect(method = "<init>*",
            at = @At(value = "NEW",
                    target = "()Ljava/util/Random;"),
            allow = 1, require = 1)
    private Random ppatches_useFasterRandom_redirectNewRandomInstance() {
        return FasterJavaRandom.newInstance(); //we can't use ThreadLocalRandom here because setSeed() is called sometimes
    }
}
