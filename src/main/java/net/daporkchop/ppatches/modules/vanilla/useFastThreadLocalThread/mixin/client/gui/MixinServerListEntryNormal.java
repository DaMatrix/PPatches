package net.daporkchop.ppatches.modules.vanilla.useFastThreadLocalThread.mixin.client.gui;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import io.netty.util.concurrent.DefaultThreadFactory;
import net.minecraft.client.gui.ServerListEntryNormal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.concurrent.ThreadFactory;

/**
 * @author DaPorkchop_
 */
@Mixin(ServerListEntryNormal.class)
abstract class MixinServerListEntryNormal {
    @Redirect(method = "<clinit>",
            at = @At(value = "INVOKE",
                    target = "Lcom/google/common/util/concurrent/ThreadFactoryBuilder;build()Ljava/util/concurrent/ThreadFactory;", remap = false),
            allow = 1, require = 1)
    private static ThreadFactory ppatches_useFastThreadLocalThread_$clinit$_useNettyThreadFactory(ThreadFactoryBuilder builder) {
        //the settings we use for the DefaultThreadFactory don't actually matter, they'll all be overridden by the ones in the ThreadFactoryBuilder
        return builder.setThreadFactory(new DefaultThreadFactory("")).build();
    }
}
