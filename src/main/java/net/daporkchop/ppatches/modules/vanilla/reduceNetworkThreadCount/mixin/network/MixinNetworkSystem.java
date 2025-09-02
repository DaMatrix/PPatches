package net.daporkchop.ppatches.modules.vanilla.reduceNetworkThreadCount.mixin.network;

import io.netty.channel.local.LocalEventLoopGroup;
import net.daporkchop.ppatches.PPatchesConfig;
import net.daporkchop.ppatches.util.mixin.ext.Delete;
import net.minecraft.network.NetworkSystem;
import net.minecraft.util.LazyLoadBase;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/**
 * @author DaPorkchop_
 */
@Mixin(NetworkSystem.class)
abstract class MixinNetworkSystem {
    //this is never used under any circumstances, so why bother keeping it around?
    @SuppressWarnings("deprecation")
    @Delete(removeStaticInitializer = true)
    @Shadow
    @Final
    public static LazyLoadBase<LocalEventLoopGroup> SERVER_LOCAL_EVENTLOOP;

    /**
     * @author DaPorkchop_
     */
    @Mixin(targets = {
            "net.minecraft.network.NetworkSystem$1", // SERVER_NIO_EVENTLOOP
            "net.minecraft.network.NetworkSystem$2", // SERVER_EPOLL_EVENTLOOP
    })
    static abstract class MixinServerNetworkEventLoopLoaders {
        @ModifyConstant(method = "load*",
                constant = @Constant(intValue = 0),
                allow = 1, require = 1)
        private int ppatches_reduceNetworkThreadCount_load_useConfiguredServerNetworkThreadCount(int zero) {
            return PPatchesConfig.vanilla_reduceNetworkThreadCount.serverNetworkEventLoopSize;
        }
    }
}
