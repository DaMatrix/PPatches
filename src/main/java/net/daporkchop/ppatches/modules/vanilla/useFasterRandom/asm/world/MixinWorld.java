package net.daporkchop.ppatches.modules.vanilla.useFasterRandom.asm.world;

import net.minecraft.world.World;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Slice;

import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

/**
 * World's constructor has two references to {@code new Random()}, the first of which is thrown away immediately. We'll ignore it.
 *
 * @author DaPorkchop_
 */
@Mixin(value = World.class, priority = 1500)
abstract class MixinWorld {
    @Redirect(method = "<init>*",
            slice = @Slice(to = @At(value = "FIELD", target = "Lnet/minecraft/world/World;updateLCG:I", opcode = Opcodes.PUTFIELD)),
            at = @At(value = "NEW",
                    target = "()Ljava/util/Random;"),
            allow = 1, require = 1)
    private Random ppatches_useFasterRandom_redirectNewRandomInstance() {
        return ThreadLocalRandom.current(); //this is a throwaway random number, we can use ThreadLocalRandom
    }
}
