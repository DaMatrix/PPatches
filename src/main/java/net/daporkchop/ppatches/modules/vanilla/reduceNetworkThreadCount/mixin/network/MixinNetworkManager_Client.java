package net.daporkchop.ppatches.modules.vanilla.reduceNetworkThreadCount.mixin.network;

import net.daporkchop.ppatches.PPatchesConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.network.NetworkManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * @author DaPorkchop_
 */
@Mixin(NetworkManager.class)
abstract class MixinNetworkManager_Client {
    @ModifyVariable(method = "createNetworkManagerAndConnect(Ljava/net/InetAddress;IZ)Lnet/minecraft/network/NetworkManager;",
            at = @At("HEAD"),
            argsOnly = true,
            allow = 1, require = 1)
    private static boolean ppatches_reduceNetworkThreadCount_createNetworkManagerAndConnect_alwaysUseNativeTransport(boolean useNativeTransport) {
        //for some reason ServerPinger hardcodes this argument to false, so we'll ensure that the argument value is always the value from the game config
        return useNativeTransport || Minecraft.getMinecraft().gameSettings.isUsingNativeTransport();
    }

    /**
     * @author DaPorkchop_
     */
    @Mixin(targets = "net.minecraft.network.NetworkManager$1")
    static abstract class MixinCLIENT_NIO_EVENTLOOP {
        @ModifyConstant(method = "load()Lio/netty/channel/nio/NioEventLoopGroup;",
                constant = @Constant(intValue = 0),
                allow = 1, require = 1)
        private int ppatches_reduceNetworkThreadCount_load_useConfiguredClientNetworkThreadCount(int zero) {
            return PPatchesConfig.vanilla_reduceNetworkThreadCount.clientNetworkEventLoopSize;
        }
    }

    /**
     * @author DaPorkchop_
     */
    @Mixin(targets = "net.minecraft.network.NetworkManager$2")
    static abstract class MixinCLIENT_EPOLL_EVENTLOOP {
        @ModifyConstant(method = "load()Lio/netty/channel/epoll/EpollEventLoopGroup;",
                constant = @Constant(intValue = 0),
                allow = 1, require = 1)
        private int ppatches_reduceNetworkThreadCount_load_useConfiguredClientNetworkThreadCount(int zero) {
            return PPatchesConfig.vanilla_reduceNetworkThreadCount.clientNetworkEventLoopSize;
        }
    }

    /**
     * @author DaPorkchop_
     */
    @Mixin(targets = "net.minecraft.network.NetworkManager$3")
    static abstract class MixinCLIENT_LOCAL_EVENTLOOP {
        @ModifyConstant(method = "load()Lio/netty/channel/local/LocalEventLoopGroup;",
                constant = @Constant(intValue = 0),
                allow = 1, require = 1)
        private int ppatches_reduceNetworkThreadCount_load_useConfiguredClientNetworkThreadCount(int zero) {
            return PPatchesConfig.vanilla_reduceNetworkThreadCount.clientNetworkEventLoopSize;
        }
    }
}
