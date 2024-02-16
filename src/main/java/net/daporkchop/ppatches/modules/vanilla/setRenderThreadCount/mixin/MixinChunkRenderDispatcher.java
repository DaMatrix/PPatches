package net.daporkchop.ppatches.modules.vanilla.setRenderThreadCount.mixin;

import net.daporkchop.ppatches.PPatchesConfig;
import net.daporkchop.ppatches.PPatchesMod;
import net.minecraft.client.renderer.chunk.ChunkRenderDispatcher;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Slice;

/**
 * @author DaPorkchop_
 */
@Mixin(ChunkRenderDispatcher.class)
abstract class MixinChunkRenderDispatcher {
    @Redirect(method = "<init>(I)V",
            slice = @Slice(
                    from = @At(value = "INVOKE:ONE",
                            target = "Ljava/lang/Runtime;availableProcessors()I"),
                    to = @At(value = "FIELD:ONE",
                            target = "Lnet/minecraft/client/renderer/chunk/ChunkRenderDispatcher;countRenderBuilders:I",
                            opcode = Opcodes.PUTFIELD)),
            at = @At(value = "INVOKE",
                    target = "Ljava/lang/Math;max(II)I"),
            allow = 1, require = 1)
    private int ppatches_setRenderThreadCount_overrideRenderThreadCount(int one, int clamp) {
        int vanillaThreadCount = Math.max(one, clamp);
        int overriddenThreadCount = PPatchesConfig.vanilla_setRenderThreadCount.renderThreadCount;
        PPatchesMod.LOGGER.info("Changing number of render threads from {} to {}", vanillaThreadCount, overriddenThreadCount);
        return overriddenThreadCount;
    }

    @ModifyConstant(method = "<init>(I)V",
            slice = @Slice(
                    from = @At(value = "FIELD:ONE",
                            target = "Lnet/minecraft/client/renderer/chunk/ChunkRenderDispatcher;countRenderBuilders:I",
                            opcode = Opcodes.PUTFIELD),
                    to = @At(value = "FIELD:ONE",
                            target = "Lnet/minecraft/client/renderer/chunk/ChunkRenderDispatcher;THREAD_FACTORY:Ljava/util/concurrent/ThreadFactory;",
                            opcode = Opcodes.GETSTATIC)),
            constant = @Constant(intValue = 1),
            allow = 1, require = 1)
    private int ppatches_setRenderThreadCount_useSeparateThreadForSingleRenderThread(int one) {
        return PPatchesConfig.vanilla_setRenderThreadCount.useSeparateThreadIfSingleRenderThread ? 0 : 1;
    }
}
