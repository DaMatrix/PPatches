package net.daporkchop.ppatches.modules.vanilla.fontRendererBatching.asm;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.renderer.vertex.VertexFormat;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * @author DaPorkchop_
 */
@Mixin(FontRenderer.class)
public abstract class MixinFontRenderer {
    @Shadow
    @Final
    protected ResourceLocation locationFontTexture;

    @Shadow
    @Final
    protected int[] charWidth;

    @Shadow
    protected float posX;

    @Shadow
    protected float posY;

    @Shadow
    private float red;
    /**
     * <h1>WARNING: This is actually blue!</h1>
     * <p>
     * MCP mappings are broken lol
     */
    @Shadow
    private float green;
    /**
     * <h1>WARNING: This is actually green!</h1>
     * <p>
     * MCP mappings are broken lol
     */
    @Shadow
    private float blue;
    @Shadow
    private float alpha;

    @Shadow
    protected abstract void bindTexture(ResourceLocation location);

    @Unique
    protected Tessellator defaultCharTessellator;
    @Unique
    protected Tessellator strikethroughUnderlineTessellator;

    @Unique
    protected float ppatches_fontRendererBatching_currentRed;
    @Unique
    protected float ppatches_fontRendererBatching_currentGreen;
    @Unique
    protected float ppatches_fontRendererBatching_currentBlue;
    @Unique
    protected float ppatches_fontRendererBatching_currentAlpha;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void ppatches_fontRendererBatching_init(CallbackInfo ci) {
        (this.defaultCharTessellator = new Tessellator(2097152)).getBuffer().begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX_COLOR);
        (this.strikethroughUnderlineTessellator = new Tessellator(2097152)).getBuffer().begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
    }

    @Unique
    protected void ppatches_fontRendererBatching_flushTessellators() {
        if (this.defaultCharTessellator.getBuffer().getVertexCount() != 0) {
            this.bindTexture(this.locationFontTexture);
            this.defaultCharTessellator.draw();
            this.defaultCharTessellator.getBuffer().begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX_COLOR);
        }

        if (this.strikethroughUnderlineTessellator.getBuffer().getVertexCount() != 0) {
            GlStateManager.disableTexture2D();
            this.strikethroughUnderlineTessellator.draw();
            GlStateManager.enableTexture2D();
            this.strikethroughUnderlineTessellator.getBuffer().begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
        }
    }

    @Inject(method = "Lnet/minecraft/client/gui/FontRenderer;renderStringAtPos(Ljava/lang/String;Z)V",
            at = @At("RETURN"),
            require = 1, allow = 1)
    private void ppatches_fontRendererBatching_flushTessellatorBuffers(String text, boolean shadow, CallbackInfo ci) {
        //we don't flush the tessellators if we're drawing the shadow pass, since we know this method will be called again immediately afterwards with the same string
        //  for the non-shadow pass, so we can keep appending to the same buffer and draw everything in one go
        if (!shadow) {
            this.ppatches_fontRendererBatching_flushTessellators();
        }
    }

    @Redirect(method = "Lnet/minecraft/client/gui/FontRenderer;doDraw(F)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/Tessellator;getInstance()Lnet/minecraft/client/renderer/Tessellator;"),
            require = 2, allow = 2)
    private Tessellator ppatches_fontRendererBatching_doDraw_useCustomTessellators() {
        return this.strikethroughUnderlineTessellator;
    }

    @Redirect(method = "Lnet/minecraft/client/gui/FontRenderer;doDraw(F)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/GlStateManager;disableTexture2D()V"),
            require = 2, allow = 2)
    private void ppatches_fontRendererBatching_doDraw_dontDisableTexture2D() {
    }

    @Redirect(method = "Lnet/minecraft/client/gui/FontRenderer;doDraw(F)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/BufferBuilder;begin(ILnet/minecraft/client/renderer/vertex/VertexFormat;)V"),
            require = 2, allow = 2)
    private void ppatches_fontRendererBatching_doDraw_dontBeginBufferBuilder(BufferBuilder builder, int mode, VertexFormat vertexFormat) {
    }

    @Redirect(method = "Lnet/minecraft/client/gui/FontRenderer;doDraw(F)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/BufferBuilder;endVertex()V"),
            require = 8, allow = 8)
    private void ppatches_fontRendererBatching_doDraw_appendColorToVertex(BufferBuilder builder) {
        builder.color(this.ppatches_fontRendererBatching_currentRed, this.ppatches_fontRendererBatching_currentGreen, this.ppatches_fontRendererBatching_currentBlue, this.ppatches_fontRendererBatching_currentAlpha).endVertex();
    }

    @Redirect(method = "Lnet/minecraft/client/gui/FontRenderer;doDraw(F)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/Tessellator;draw()V"),
            require = 2, allow = 2)
    private void ppatches_fontRendererBatching_doDraw_dontDraw(Tessellator tessellator) {
    }

    @Redirect(method = "Lnet/minecraft/client/gui/FontRenderer;doDraw(F)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/GlStateManager;enableTexture2D()V"),
            require = 2, allow = 2)
    private void ppatches_fontRendererBatching_doDraw_dontEnableTexture2D() {
    }

    @Redirect(method = "Lnet/minecraft/client/gui/FontRenderer;setColor(FFFF)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/GlStateManager;color(FFFF)V"),
            allow = 1, require = 1)
    private void ppatches_fontRendererBatching_setColor_dontSetGlColor(float r, float g, float b, float a) {
        this.ppatches_fontRendererBatching_currentRed = r;
        this.ppatches_fontRendererBatching_currentGreen = g;
        this.ppatches_fontRendererBatching_currentBlue = b;
        this.ppatches_fontRendererBatching_currentAlpha = a;
    }

    /**
     * @reason avoid using the fixed-function glVertex* methods for drawing, because it's not 1995 and we aren't savages
     * @author DaPorkchop_
     */
    @Overwrite
    protected float renderDefaultChar(int ch, boolean italic) {
        int i = ch % 16 * 8;
        int j = ch / 16 * 8;
        int k = italic ? 1 : 0;
        int l = this.charWidth[ch]; //TODO: OptiFine uses charWidthFloat here
        double f = l - 0.01d;

        final double TEX_COORD_OFFSET = 7.99d;
        final double TEX_COORD_SCALE = 1.0d / 128.0d;

        BufferBuilder bufferbuilder = this.defaultCharTessellator.getBuffer();
        bufferbuilder.pos(this.posX - k, this.posY + TEX_COORD_OFFSET, 0.0d).tex(i * TEX_COORD_SCALE, (j + TEX_COORD_OFFSET) * TEX_COORD_SCALE).color(this.ppatches_fontRendererBatching_currentRed, this.ppatches_fontRendererBatching_currentGreen, this.ppatches_fontRendererBatching_currentBlue, this.ppatches_fontRendererBatching_currentAlpha).endVertex();
        bufferbuilder.pos(this.posX + f - 1.0d - k, this.posY + TEX_COORD_OFFSET, 0.0d).tex((i + f - 1.0d) * TEX_COORD_SCALE, (j + TEX_COORD_OFFSET) * TEX_COORD_SCALE).color(this.ppatches_fontRendererBatching_currentRed, this.ppatches_fontRendererBatching_currentGreen, this.ppatches_fontRendererBatching_currentBlue, this.ppatches_fontRendererBatching_currentAlpha).endVertex();
        bufferbuilder.pos(this.posX + f - 1.0d + k, this.posY, 0.0d).tex((i + f - 1.0d) * TEX_COORD_SCALE, j * TEX_COORD_SCALE).color(this.ppatches_fontRendererBatching_currentRed, this.ppatches_fontRendererBatching_currentGreen, this.ppatches_fontRendererBatching_currentBlue, this.ppatches_fontRendererBatching_currentAlpha).endVertex();
        bufferbuilder.pos(this.posX + k, this.posY, 0.0d).tex(i * TEX_COORD_SCALE, j * TEX_COORD_SCALE).color(this.ppatches_fontRendererBatching_currentRed, this.ppatches_fontRendererBatching_currentGreen, this.ppatches_fontRendererBatching_currentBlue, this.ppatches_fontRendererBatching_currentAlpha).endVertex();
        return l;
    }
}
