package net.daporkchop.ppatches.modules.vanilla.useFastThreadLocalThread.mixin.fml.common.thread;

import io.netty.util.concurrent.FastThreadLocal;
import io.netty.util.concurrent.FastThreadLocalThread;
import net.minecraftforge.fml.common.thread.SidedThreadGroup;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * @author DaPorkchop_
 */
@Mixin(value = SidedThreadGroup.class, remap = false)
abstract class MixinSidedThreadGroup {
    @Redirect(method = "newThread(Ljava/lang/Runnable;)Ljava/lang/Thread;",
            at = @At(value = "NEW",
                    target = "(Ljava/lang/ThreadGroup;Ljava/lang/Runnable;)Ljava/lang/Thread;"),
            allow = 1, require = 1)
    private Thread ppatches_useFastThreadLocalThread_newThread_createFastThreadLocalThread(ThreadGroup group, Runnable target) {
        //wrap the runnable so that it cleans up FastThreadLocals when it exits, as if it were created by Netty's DefaultThreadFactory
        return new FastThreadLocalThread(group, () -> {
            try {
                target.run();
            } finally {
                FastThreadLocal.removeAll();
            }
        });
    }
}
