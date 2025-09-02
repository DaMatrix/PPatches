package net.daporkchop.ppatches.modules.vanilla.reduceNetworkThreadCount.mixin.client.gui;

import net.daporkchop.ppatches.PPatchesConfig;
import net.minecraft.client.gui.ServerListEntryNormal;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

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

    @ModifyConstant(method = "<clinit>",
            constant = @Constant(intValue = 5),
            allow = 1, require = 1)
    private static int ppatches_reduceNetworkThreadCount_$clinit$_limitServerPingThreads(int ignored) {
        return PPatchesConfig.vanilla_reduceNetworkThreadCount.serverPingThreadCount;
    }

    @Inject(method = "<clinit>",
            at = @At(value = "FIELD",
                    target = "Lnet/minecraft/client/gui/ServerListEntryNormal;EXECUTOR:Ljava/util/concurrent/ThreadPoolExecutor;",
                    opcode = Opcodes.PUTSTATIC,
                    shift = At.Shift.AFTER),
            allow = 1, require = 1)
    private static void ppatches_reduceNetworkThreadCount_$clinit$_makeServerPingThreadsTimeout(CallbackInfo ci) {
        int keepaliveTime = PPatchesConfig.vanilla_reduceNetworkThreadCount.serverPingThreadKeepAliveTime;
        if (keepaliveTime >= 0) {
            EXECUTOR.setKeepAliveTime(keepaliveTime, TimeUnit.SECONDS);
            EXECUTOR.allowCoreThreadTimeOut(true);
        }

        //don't allow more than
        EXECUTOR.setMaximumPoolSize(EXECUTOR.getCorePoolSize());
    }
}
