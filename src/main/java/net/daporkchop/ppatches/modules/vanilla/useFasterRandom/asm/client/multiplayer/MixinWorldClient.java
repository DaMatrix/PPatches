package net.daporkchop.ppatches.modules.vanilla.useFasterRandom.asm.client.multiplayer;

import net.minecraft.client.multiplayer.WorldClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

/**
 * @author DaPorkchop_
 */
@Mixin(WorldClient.class)
abstract class MixinWorldClient {
    @Redirect(method = "Lnet/minecraft/client/multiplayer/WorldClient;doVoidFogParticles(III)V",
            at = @At(value = "NEW",
                    target = "()Ljava/util/Random;"),
            allow = 1, require = 1)
    private Random ppatches_useFasterRandom_redirectNewRandomInstance() {
        return ThreadLocalRandom.current();
    }
}
