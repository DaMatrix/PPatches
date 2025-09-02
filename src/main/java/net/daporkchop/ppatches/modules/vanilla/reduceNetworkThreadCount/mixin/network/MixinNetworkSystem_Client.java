package net.daporkchop.ppatches.modules.vanilla.reduceNetworkThreadCount.mixin.network;

import io.netty.channel.EventLoopGroup;
import io.netty.channel.epoll.Epoll;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import net.daporkchop.ppatches.PPatchesConfig;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.NetworkSystem;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.LazyLoadBase;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * @author DaPorkchop_
 */
@Mixin(NetworkSystem.class)
abstract class MixinNetworkSystem_Client {
    @Shadow
    @Final
    @Mutable
    public static LazyLoadBase<EpollEventLoopGroup> SERVER_EPOLL_EVENTLOOP;

    @Shadow
    @Final
    @Mutable
    public static LazyLoadBase<NioEventLoopGroup> SERVER_NIO_EVENTLOOP;

    @Shadow
    @Final
    private MinecraftServer server;

    @Inject(method = "<clinit>",
            at = @At("RETURN"),
            allow = 1, require = 1)
    private static void ppatches_reduceNetworkThreadCount_$clinit$_useClientNetworkEventLoopsOnServer(CallbackInfo ci) {
        if (PPatchesConfig.vanilla_reduceNetworkThreadCount.useClientNetworkEventLoopOnIntegratedServer) {
            SERVER_NIO_EVENTLOOP = NetworkManager.CLIENT_NIO_EVENTLOOP;
            SERVER_EPOLL_EVENTLOOP = NetworkManager.CLIENT_EPOLL_EVENTLOOP;
        }
    }

    @Redirect(method = "addLocalEndpoint()Ljava/net/SocketAddress;",
            at = @At(value = "FIELD",
                    target = "Lnet/minecraft/network/NetworkSystem;SERVER_NIO_EVENTLOOP:Lnet/minecraft/util/LazyLoadBase;",
                    opcode = Opcodes.GETSTATIC),
            allow = 1, require = 1)
    private LazyLoadBase<? extends EventLoopGroup> ppatches_reduceNetworkThreadCount_addLocalEndpoint_useClientLocalEventLoopOnIntegratedServer() {
        if (PPatchesConfig.vanilla_reduceNetworkThreadCount.useClientLocalEventLoopOnIntegratedServer) {
            //the dedicated server can use the client's local event loop - it really only needs one thread, since all it does is copy buffers from one queue to another.
            return NetworkManager.CLIENT_LOCAL_EVENTLOOP;
        } else {
            return Epoll.isAvailable() && this.server.shouldUseNativeTransport() ? SERVER_EPOLL_EVENTLOOP : SERVER_NIO_EVENTLOOP;
        }
    }
}
