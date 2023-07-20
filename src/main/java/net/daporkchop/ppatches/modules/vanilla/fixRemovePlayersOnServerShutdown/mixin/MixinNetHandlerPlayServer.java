package net.daporkchop.ppatches.modules.vanilla.fixRemovePlayersOnServerShutdown.mixin;

import net.daporkchop.ppatches.PPatchesMod;
import net.minecraft.network.NetHandlerPlayServer;
import net.minecraft.network.NetworkManager;
import net.minecraft.util.text.ITextComponent;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * @author DaPorkchop_
 */
@Mixin(NetHandlerPlayServer.class)
abstract class MixinNetHandlerPlayServer {
    @Shadow
    @Final
    public NetworkManager netManager;

    @Inject(method = "Lnet/minecraft/network/NetHandlerPlayServer;disconnect(Lnet/minecraft/util/text/ITextComponent;)V",
            at = @At("HEAD"),
            cancellable = true,
            allow = 1, require = 1)
    private void ppatches_fixRemovePlayersOnServerShutdown_disconnect_dontDisconnectIfAlreadyDisconnected(ITextComponent textComponent, CallbackInfo ci) {
        if (!this.netManager.channel().config().isAutoRead()) {
            PPatchesMod.LOGGER.info("vanilla.fixRemovePlayersOnServerShutdown: skipping player disconnect (autoRead was already false, assuming already disconnected)");
            ci.cancel();
        }
    }
}
