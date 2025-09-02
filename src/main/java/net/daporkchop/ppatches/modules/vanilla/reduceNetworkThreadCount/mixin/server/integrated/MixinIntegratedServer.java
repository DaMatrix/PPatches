package net.daporkchop.ppatches.modules.vanilla.reduceNetworkThreadCount.mixin.server.integrated;

import net.minecraft.client.Minecraft;
import net.minecraft.server.integrated.IntegratedServer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

/**
 * @author DaPorkchop_
 */
@Mixin(IntegratedServer.class)
abstract class MixinIntegratedServer {
    @Shadow
    @Final
    private Minecraft mc;

    /**
     * @author DaPorkchop_
     * @reason we're replacing the whole method
     */
    @Overwrite
    public boolean shouldUseNativeTransport() {
        //use the client settings to decide whether to enable epoll for the integrated sever
        return this.mc.gameSettings.isUsingNativeTransport();
    }
}
