package net.daporkchop.ppatches.modules.vanilla.fixRemovePlayersOnServerShutdown.asm;

import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.io.IOException;
import java.io.PrintStream;

/**
 * @author DaPorkchop_
 */
@Mixin(MinecraftServer.class)
abstract class MixinMinecraftServer_Server {
    @Redirect(method = "Lnet/minecraft/server/MinecraftServer;main([Ljava/lang/String;)V", remap = false,
            at = @At(value = "INVOKE",
                    target = "Ljava/lang/Runtime;addShutdownHook(Ljava/lang/Thread;)V"),
            allow = 1, require = 1)
    private static void ppatches_test_addShutdownHook(Runtime runtime, Thread thread) {
        thread.setUncaughtExceptionHandler((t, e) -> {
            try (PrintStream stream = new PrintStream("/dev/stderr")) {
                e.printStackTrace(stream);
            } catch (IOException ex) {
                throw new RuntimeException(ex);
            }
        });
        runtime.addShutdownHook(thread);
    }
}
