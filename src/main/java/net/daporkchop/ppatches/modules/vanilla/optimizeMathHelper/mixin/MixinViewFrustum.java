package net.daporkchop.ppatches.modules.vanilla.optimizeMathHelper.mixin;

import net.minecraft.client.renderer.ViewFrustum;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * @author DaPorkchop_
 */
@Mixin(ViewFrustum.class)
abstract class MixinViewFrustum {
    @Redirect(method = "Lnet/minecraft/client/renderer/ViewFrustum;markBlocksForUpdate(IIIIIIZ)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/util/math/MathHelper;intFloorDiv(II)I"),
            allow = 6, require = 6)
    private int ppatches_optimizeMathHelper_markBlocksForUpdate_shiftInsteadOfFloorDiv(int coord, int sixteen) {
        if (sixteen != 16) throw new AssertionError(sixteen); //this call will be folded away once this method is inlined
        return coord >> 4;
    }

    @Redirect(method = "Lnet/minecraft/client/renderer/ViewFrustum;getRenderChunk(Lnet/minecraft/util/math/BlockPos;)Lnet/minecraft/client/renderer/chunk/RenderChunk;",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/util/math/MathHelper;intFloorDiv(II)I"),
            allow = 3, require = 0)
    private int ppatches_optimizeMathHelper_getRenderChunk_shiftInsteadOfFloorDiv(int coord, int sixteen) {
        if (sixteen != 16) throw new AssertionError(sixteen); //this call will be folded away once this method is inlined
        return coord >> 4;
    }
}
