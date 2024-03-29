package net.daporkchop.ppatches.modules.vanilla.useFastThreadLocalThread.mixin;

import io.netty.util.concurrent.FastThreadLocalThread;
import io.netty.util.internal.InternalThreadLocalMap;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * @author DaPorkchop_
 */
@Mixin(MinecraftServer.class)
abstract class MixinMinecraftServer {
    @Redirect(method = "startServerThread()V",
            at = @At(value = "NEW",
                    target = "(Ljava/lang/ThreadGroup;Ljava/lang/Runnable;Ljava/lang/String;)Ljava/lang/Thread;"),
            allow = 1, require = 1)
    private Thread ppatches_useFastThreadLocalThread_startServerThread_constructServerThreadAsFastThreadLocalThread(ThreadGroup group, Runnable target, String name) {
        return new FastThreadLocalThread(group, target, name);
    }

    @Inject(method = "run()V",
            at = @At("HEAD"),
            allow = 1, require = 1)
    private void ppatches_useFastThreadLocalThread_run_initInternalThreadLocalMap(CallbackInfo ci) {
        //ensure that the InternalThreadLocalMap is already initialized before we do anything
        InternalThreadLocalMap.get();
    }
}
