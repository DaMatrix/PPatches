package net.daporkchop.ppatches.modules.vanilla.reduceNetworkThreadCount.mixin.client.network;

import io.netty.channel.EventLoopGroup;
import io.netty.channel.epoll.Epoll;
import io.netty.channel.epoll.EpollSocketChannel;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.network.ServerPinger;
import net.minecraft.network.NetworkManager;
import net.minecraft.util.LazyLoadBase;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * @author DaPorkchop_
 */
@Mixin(ServerPinger.class)
abstract class MixinServerPinger {
    @Redirect(method = "tryCompatibilityPing(Lnet/minecraft/client/multiplayer/ServerData;)V",
            at = @At(value = "FIELD",
                    target = "Lnet/minecraft/network/NetworkManager;CLIENT_NIO_EVENTLOOP:Lnet/minecraft/util/LazyLoadBase;",
                    opcode = Opcodes.GETSTATIC),
            allow = 1, require = 1)
    private LazyLoadBase<? extends EventLoopGroup> ppatches_reduceNetworkThreadCount_tryCompatibilityPing_tryNativeEventLoop() {
        return Epoll.isAvailable() && Minecraft.getMinecraft().gameSettings.isUsingNativeTransport()
                ? NetworkManager.CLIENT_EPOLL_EVENTLOOP
                : NetworkManager.CLIENT_NIO_EVENTLOOP;
    }

    @ModifyConstant(method = "tryCompatibilityPing(Lnet/minecraft/client/multiplayer/ServerData;)V",
            constant = @Constant(classValue = NioSocketChannel.class),
            allow = 1, require = 1)
    private Class<? extends SocketChannel> ppatches_reduceNetworkThreadCount_tryCompatibilityPing_tryNativeSocketChannel(Class<?> ignored) {
        return Epoll.isAvailable() && Minecraft.getMinecraft().gameSettings.isUsingNativeTransport()
                ? EpollSocketChannel.class
                : NioSocketChannel.class;
    }
}
