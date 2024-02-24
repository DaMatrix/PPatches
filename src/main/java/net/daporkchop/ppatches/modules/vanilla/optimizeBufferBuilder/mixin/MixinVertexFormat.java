package net.daporkchop.ppatches.modules.vanilla.optimizeBufferBuilder.mixin;

import com.google.common.base.Preconditions;
import net.daporkchop.ppatches.modules.vanilla.optimizeBufferBuilder.OptimizedVertexFormat;
import net.daporkchop.ppatches.modules.vanilla.optimizeBufferBuilder.util.IMixinVertexFormat_OptimizeBufferBuilder;
import net.minecraft.client.renderer.vertex.VertexFormat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * @author DaPorkchop_
 */
@Mixin(VertexFormat.class)
abstract class MixinVertexFormat implements IMixinVertexFormat_OptimizeBufferBuilder {
    @Shadow
    public abstract int getSize();

    @Unique
    private OptimizedVertexFormat ppatches_optimizeBufferBuilder_optimizedVertexFormat;

    @Inject(method = "<init>()V",
            at = @At("RETURN"),
            allow = 1, require = 1)
    private void ppatches_optimizeBufferBuilder_$init$_ensureNotSubclassed(CallbackInfo ci) {
        Preconditions.checkState(this.getClass() == MixinVertexFormat.class, "ppatches.optimizeBufferBuilder: a mod added a subclass of VertexFormat!");
    }

    @Inject(method = "clear()V",
            at = @At("HEAD"),
            allow = 1, require = 1)
    private synchronized void ppatches_optimizeBufferBuilder_clear_clearOptimizedVertexFormatOnChange(CallbackInfo ci) {
        this.ppatches_optimizeBufferBuilder_optimizedVertexFormat = null;
    }

    @Inject(method = "addElement(Lnet/minecraft/client/renderer/vertex/VertexFormatElement;)Lnet/minecraft/client/renderer/vertex/VertexFormat;",
            at = @At("HEAD"),
            allow = 1, require = 1)
    private synchronized void ppatches_optimizeBufferBuilder_addElement_clearOptimizedVertexFormatOnChange(CallbackInfoReturnable<VertexFormat> ci) {
        this.ppatches_optimizeBufferBuilder_optimizedVertexFormat = null;
    }

    @Override
    public final OptimizedVertexFormat ppatches_optimizeBufferBuilder_optimizedFormat() {
        OptimizedVertexFormat format = this.ppatches_optimizeBufferBuilder_optimizedVertexFormat;
        return format != null ? format : this.ppatches_optimizeBufferBuilder_computeOptimizedFormat();
    }

    private synchronized OptimizedVertexFormat ppatches_optimizeBufferBuilder_computeOptimizedFormat() {
        if (this.ppatches_optimizeBufferBuilder_optimizedVertexFormat != null) {
            return this.ppatches_optimizeBufferBuilder_optimizedVertexFormat;
        }

        return this.ppatches_optimizeBufferBuilder_optimizedVertexFormat = OptimizedVertexFormat.buildOptimizedVertexFormat((VertexFormat) (Object) this);
    }

    /**
     * @author DaPorkchop_
     * @reason we're replacing the whole method
     */
    @Overwrite
    public int getIntegerSize() {
        //use a shift instead of dividing by four (we assume the size isn't negative, so this saves us a CMOV which isn't necessary using a shift)
        return this.getSize() >> 2;
    }
}
