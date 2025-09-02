package net.daporkchop.ppatches.modules.vanilla.useFastThreadLocalThread.mixin.network;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import io.netty.util.concurrent.DefaultThreadFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.concurrent.ThreadFactory;

/**
 * @author DaPorkchop_
 */
@Mixin(targets = "net.minecraft.network.NetworkManager$3")
abstract class MixinNetworkManager$CLIENT_LOCAL_EVENTLOOP {
    @Redirect(method = "load()Lio/netty/channel/local/LocalEventLoopGroup;",
            at = @At(value = "INVOKE",
                    target = "Lcom/google/common/util/concurrent/ThreadFactoryBuilder;build()Ljava/util/concurrent/ThreadFactory;", remap = false),
            allow = 1, require = 1)
    private ThreadFactory ppatches_useFastThreadLocalThread_load_useNettyThreadFactory(ThreadFactoryBuilder builder) {
        //the settings we use for the DefaultThreadFactory don't actually matter, they'll all be overridden by the ones in the ThreadFactoryBuilder
        return builder.setThreadFactory(new DefaultThreadFactory("")).build();
    }
}
