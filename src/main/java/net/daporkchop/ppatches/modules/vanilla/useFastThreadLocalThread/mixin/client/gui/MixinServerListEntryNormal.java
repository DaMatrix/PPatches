package net.daporkchop.ppatches.modules.vanilla.useFastThreadLocalThread.mixin.client.gui;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import io.netty.util.concurrent.DefaultThreadFactory;
import net.minecraft.client.gui.ServerListEntryNormal;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * @author DaPorkchop_
 */
@Mixin(ServerListEntryNormal.class)
abstract class MixinServerListEntryNormal {
    @Shadow
    @Final
    private static ThreadPoolExecutor EXECUTOR;

    @Redirect(method = "<clinit>",
            at = @At(value = "INVOKE",
                    target = "Lcom/google/common/util/concurrent/ThreadFactoryBuilder;build()Ljava/util/concurrent/ThreadFactory;", remap = false),
            allow = 1, require = 1)
    private static ThreadFactory ppatches_useFastThreadLocalThread_$clinit$_useNettyThreadFactory(ThreadFactoryBuilder builder) {
        //the settings we use for the DefaultThreadFactory don't actually matter, they'll all be overridden by the ones in the ThreadFactoryBuilder
        return builder.setThreadFactory(new DefaultThreadFactory("")).build();
    }

    @Inject(method = "<clinit>",
            at = @At(value = "FIELD",
                    target = "Lnet/minecraft/client/gui/ServerListEntryNormal;EXECUTOR:Ljava/util/concurrent/ThreadPoolExecutor;",
                    opcode = Opcodes.PUTSTATIC,
                    shift = At.Shift.AFTER),
            allow = 1, require = 1)
    private static void ppatches_useFastThreadLocalThread_$clinit$_makeServerPingThreadsTimeout(CallbackInfo ci) {
        EXECUTOR.setKeepAliveTime(1L, TimeUnit.MINUTES);
        EXECUTOR.allowCoreThreadTimeOut(true);
    }
}
