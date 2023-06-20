package net.daporkchop.ppatches.modules.vanilla.fixRemovePlayersOnServerShutdown.asm;

import lombok.SneakyThrows;
import net.minecraft.server.MinecraftServer;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.io.FileNotFoundException;
import java.io.PrintStream;

/**
 * @author DaPorkchop_
 */
@Mixin(MinecraftServer.class)
abstract class MixinMinecraftServer {
    @Shadow
    @Final
    private static Logger LOGGER;

    @Inject(method = "Lnet/minecraft/server/MinecraftServer;stopServer()V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/server/management/PlayerList;removeAllPlayers()V"),
            allow = 1, require = 1)
    private void ppatches_test_printBeforeRemoveAllPlayers(CallbackInfo ci) {
        this.ppatches_test_redirectLogger(LOGGER, "stopServer(): pre  removeAllPlayers()");
    }

    @Inject(method = "Lnet/minecraft/server/MinecraftServer;stopServer()V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/server/management/PlayerList;removeAllPlayers()V",
                    shift = At.Shift.AFTER),
            allow = 1, require = 1)
    private void ppatches_test_printAfterRemoveAllPlayers(CallbackInfo ci) {
        this.ppatches_test_redirectLogger(LOGGER, "stopServer(): post removeAllPlayers()");
    }

    @Redirect(method = "Lnet/minecraft/server/MinecraftServer;stopServer()V",
            at = @At(value = "INVOKE",
                    target = "Lorg/apache/logging/log4j/Logger;info(Ljava/lang/String;)V"),
            allow = 3, require = 3)
    @SneakyThrows(FileNotFoundException.class)
    private void ppatches_test_redirectLogger(Logger logger, String msg) {
        if ("Server Shutdown Thread".equals(Thread.currentThread().getName())) {
            try (PrintStream stream = new PrintStream("/dev/stderr")) {
                stream.println("[Server Shutdown Thread/INFO] " + msg);
            }
        } else {
            logger.info(msg);
        }
    }
}
