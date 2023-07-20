package net.daporkchop.ppatches.modules.vanilla.useFasterRandom.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

/**
 * @author DaPorkchop_
 */
@Mixin(value = {
        net.minecraft.client.audio.MusicTicker.class,
        net.minecraft.client.audio.SoundEventAccessor.class,
        net.minecraft.client.gui.GuiCustomizeWorldScreen.class,
        net.minecraft.client.gui.GuiEnchantment.class,
        net.minecraft.client.gui.spectator.categories.TeleportToTeam.TeamSelectionObject.class,
        net.minecraft.client.network.NetHandlerPlayClient.class,
        net.minecraft.client.particle.Particle.class,
        net.minecraft.client.particle.ParticleManager.class,
        net.minecraft.client.renderer.entity.RenderEnderman.class,
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
