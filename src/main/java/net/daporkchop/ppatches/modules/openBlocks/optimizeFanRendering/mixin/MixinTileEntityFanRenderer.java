package net.daporkchop.ppatches.modules.openBlocks.optimizeFanRendering.mixin;

import net.daporkchop.ppatches.modules.openBlocks.optimizeFanRendering.OptimizedFanBladesRenderer;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.client.model.animation.FastTESR;
import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * @author DaPorkchop_
 */
@Pseudo
@Mixin(targets = "openblocks.client.renderer.tileentity.TileEntityFanRenderer", remap = false)
abstract class MixinTileEntityFanRenderer extends FastTESR<TileEntity> {
    @Dynamic
    @Inject(method = "renderTileEntityFast",
            at = @At("HEAD"),
            cancellable = true,
            allow = 1, require = 1)
    private void ppatches_optimizeFanRendering_skipTESR(@Coerce TileEntity te, double x, double y, double z, float partialTicks, int destroyStage, float alpha, BufferBuilder renderer, CallbackInfo ci) {
        float fanAngle = (float) Math.toRadians(((IMixinTileEntityFan) te).callGetAngle());
        float bladeAngle = (float) Math.toRadians(((IMixinTileEntityFan) te).callGetBladeRotation(partialTicks));
        OptimizedFanBladesRenderer.renderFanBladesFast(te, (float) x, (float) y, (float) z, fanAngle, bladeAngle, renderer);

        ci.cancel();
    }
}
