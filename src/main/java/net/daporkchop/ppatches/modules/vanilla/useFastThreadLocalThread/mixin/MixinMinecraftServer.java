package net.daporkchop.ppatches.modules.vanilla.useFastThreadLocalThread.mixin;

import io.netty.util.concurrent.FastThreadLocalThread;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * @author DaPorkchop_
 */
@Mixin(MinecraftServer.class)
abstract class MixinMinecraftServer {
    @Redirect(method = "Lnet/minecraft/server/MinecraftServer;startServerThread()V",
            at = @At(value = "NEW",
                    target = "(Ljava/lang/ThreadGroup;Ljava/lang/Runnable;Ljava/lang/String;)Ljava/lang/Thread;"),
            allow = 1, require = 1)
    private Thread ppatches_useFastThreadLocalThread_constructServerThreadAsFastThreadLocalThread(ThreadGroup group, Runnable target, String name) {
        return new FastThreadLocalThread(group, target, name);
    }
}
