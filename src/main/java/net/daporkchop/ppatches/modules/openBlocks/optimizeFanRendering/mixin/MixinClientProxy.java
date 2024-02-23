package net.daporkchop.ppatches.modules.openBlocks.optimizeFanRendering.mixin;

import net.daporkchop.ppatches.modules.openBlocks.optimizeFanRendering.OptimizedFanBladesRenderer;
import net.minecraftforge.common.MinecraftForge;
import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * @author DaPorkchop_
 */
@Pseudo
@Mixin(targets = "openblocks.client.ClientProxy", remap = false)
abstract class MixinClientProxy {
    @Dynamic
    @Inject(method = "preInit()V",
            at = @At("HEAD"),
            allow = 1, require = 1)
    private void ppatches_optimizeFanRendering_registerRenderer(CallbackInfo ci) {
        MinecraftForge.EVENT_BUS.register(OptimizedFanBladesRenderer.class);
    }
}
