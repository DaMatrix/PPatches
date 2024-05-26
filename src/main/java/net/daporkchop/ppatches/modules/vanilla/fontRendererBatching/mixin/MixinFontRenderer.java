package net.daporkchop.ppatches.modules.vanilla.fontRendererBatching.mixin;

import com.google.common.base.Preconditions;
import net.daporkchop.ppatches.modules.vanilla.fontRendererBatching.IBatchingFontRenderer;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.renderer.vertex.VertexFormat;
import net.minecraft.client.resources.IResource;
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
import org.spongepowered.asm.mixin.injection.Surrogate;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

import java.awt.image.BufferedImage;
import java.util.Properties;

/**
 * @author DaPorkchop_
 */
@Mixin(FontRenderer.class)
abstract class MixinFontRenderer implements IBatchingFontRenderer {
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
    protected abstract void bindTexture(ResourceLocation location);

    @Shadow
    protected abstract void loadGlyphTexture(int page);

    @Unique
    protected Tessellator ppatches_fontRendererBatching_defaultCharTessellator;

    @Unique
    protected float ppatches_fontRendererBatching_currentRed;
    @Unique
    protected float ppatches_fontRendererBatching_currentGreen;
    @Unique
    protected float ppatches_fontRendererBatching_currentBlue;
    @Unique
    protected float ppatches_fontRendererBatching_currentAlpha;

    @Unique
    protected float ppatches_fontRendererBatching_whitePixelX;
    @Unique
    protected float ppatches_fontRendererBatching_whitePixelY;

    @Unique
    protected boolean ppatches_fontRendererBatching_drawnAnyUnicode;

    @Unique
    protected boolean ppatches_fontRendererBatching_isRenderingBatch;

    @Override
    public final void ppatches_fontRendererBatching_beginBatchedRendering() {
        Preconditions.checkState(!this.ppatches_fontRendererBatching_isRenderingBatch, "a font rendering batch has already been started!");
        this.ppatches_fontRendererBatching_isRenderingBatch = true;
    }

    @Override
    public final void ppatches_fontRendererBatching_endBatchedRendering() {
        Preconditions.checkState(this.ppatches_fontRendererBatching_isRenderingBatch, "no font rendering batch is currently active!");
        this.ppatches_fontRendererBatching_isRenderingBatch = false;
        this.ppatches_fontRendererBatching_flushTessellators();
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void ppatches_fontRendererBatching_init(CallbackInfo ci) {
        (this.ppatches_fontRendererBatching_defaultCharTessellator = new Tessellator(16)).getBuffer().begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX_COLOR);
    }

    @Unique
    protected void ppatches_fontRendererBatching_flushTessellators() {
        if (this.ppatches_fontRendererBatching_defaultCharTessellator.getBuffer().getVertexCount() != 0) {
            this.bindTexture(this.locationFontTexture);
            this.ppatches_fontRendererBatching_defaultCharTessellator.draw();
            this.ppatches_fontRendererBatching_defaultCharTessellator.getBuffer().begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX_COLOR);
        }
    }

    //VanillaFix compatibility: make sure the tessellator is always drawing when we start rendering a new string - for some reason VanillaFix resets ALL BufferBuilder
    // instances when the game crashes
    @Inject(method = "Lnet/minecraft/client/gui/FontRenderer;renderStringAtPos(Ljava/lang/String;Z)V",
            at = @At("HEAD"),
            require = 1, allow = 1) //TODO: maybe guard this behind a constraint?
    private void ppatches_fontRendererBatching_renderStringAtPos_beginTessellating(CallbackInfo ci) {
        if (!this.ppatches_fontRendererBatching_defaultCharTessellator.getBuffer().isDrawing) {
            this.ppatches_fontRendererBatching_defaultCharTessellator.getBuffer().begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX_COLOR);
        }
    }

    @Inject(method = "Lnet/minecraft/client/gui/FontRenderer;renderStringAtPos(Ljava/lang/String;Z)V",
            at = @At("RETURN"),
            require = 1, allow = 1)
    private void ppatches_fontRendererBatching_renderStringAtPos_flushTessellatorBuffers(String text, boolean shadow, CallbackInfo ci) {
        //we don't flush the tessellators if we're drawing the shadow pass, since we know this method will be called again immediately afterwards with the same string
        //  for the non-shadow pass, so we can keep appending to the same buffer and draw everything in one go. this is also the case if we're currently drawing a multi-
        //  string batch - we won't flush the tessellator and will keep appending to the same buffer until explicitly instructed to flush.
        //however, this optimization isn't possible if any unicode symbols were present in the string, as they're not present in the buffer and are therefore
        //  out-of-order with respect to the other characters, which will cause them to overlap strangely with the shadow text
        if ((!shadow & !this.ppatches_fontRendererBatching_isRenderingBatch) | this.ppatches_fontRendererBatching_drawnAnyUnicode) {
            this.ppatches_fontRendererBatching_drawnAnyUnicode = false;
            this.ppatches_fontRendererBatching_flushTessellators();
        }
    }

    @Inject(method = "Lnet/minecraft/client/gui/FontRenderer;readFontTexture()V",
            at = @At(value = "INVOKE", target = "Ljava/awt/image/BufferedImage;getRGB(IIII[III)[I", shift = At.Shift.AFTER),
            locals = LocalCapture.CAPTURE_FAILHARD,
            allow = 1, require = 1)
    private void ppatches_fontRendererBatching_readFontTexture_findWhitePixel(CallbackInfo ci, IResource resource, BufferedImage bufferedImage, int width, int height, int[] pixels) {
        for (int i = 0; i < pixels.length; i++) {
            if (pixels[i] == -1) { //an opaque white pixel
                int px = i / height;
                int py = i % height;

                this.ppatches_fontRendererBatching_whitePixelX = px / 128.0f;
                this.ppatches_fontRendererBatching_whitePixelY = py / 128.0f;
                return;
            }
        }

        throw new IllegalStateException("couldn't find any solid white pixels on ascii.png!");
    }

    // OptiFine compatibility
    @Surrogate
    private void ppatches_fontRendererBatching_readFontTexture_findWhitePixel(CallbackInfo ci, IResource resource, BufferedImage bufferedImage, Properties properties, int width, int height) {
        int[] pixels = new int[width * height];
        bufferedImage.getRGB(0, 0, width, height, pixels, 0, width);
        this.ppatches_fontRendererBatching_readFontTexture_findWhitePixel(ci, resource, bufferedImage, width, height, pixels);
    }

    @Redirect(method = "Lnet/minecraft/client/gui/FontRenderer;doDraw(F)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/Tessellator;getInstance()Lnet/minecraft/client/renderer/Tessellator;"),
            require = 2, allow = 2)
    private Tessellator ppatches_fontRendererBatching_doDraw_useCustomTessellators() {
        return this.ppatches_fontRendererBatching_defaultCharTessellator;
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
        builder.tex(this.ppatches_fontRendererBatching_whitePixelX, this.ppatches_fontRendererBatching_whitePixelY)
                .color(this.ppatches_fontRendererBatching_currentRed, this.ppatches_fontRendererBatching_currentGreen, this.ppatches_fontRendererBatching_currentBlue, this.ppatches_fontRendererBatching_currentAlpha)
                .endVertex();
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

        BufferBuilder bufferbuilder = this.ppatches_fontRendererBatching_defaultCharTessellator.getBuffer();
        bufferbuilder.pos(this.posX - k, this.posY + TEX_COORD_OFFSET, 0.0d)
                .tex(i * TEX_COORD_SCALE, (j + TEX_COORD_OFFSET) * TEX_COORD_SCALE)
                .color(this.ppatches_fontRendererBatching_currentRed, this.ppatches_fontRendererBatching_currentGreen, this.ppatches_fontRendererBatching_currentBlue, this.ppatches_fontRendererBatching_currentAlpha)
                .endVertex();
        bufferbuilder.pos(this.posX + f - 1.0d - k, this.posY + TEX_COORD_OFFSET, 0.0d)
                .tex((i + f - 1.0d) * TEX_COORD_SCALE, (j + TEX_COORD_OFFSET) * TEX_COORD_SCALE)
                .color(this.ppatches_fontRendererBatching_currentRed, this.ppatches_fontRendererBatching_currentGreen, this.ppatches_fontRendererBatching_currentBlue, this.ppatches_fontRendererBatching_currentAlpha)
                .endVertex();
        bufferbuilder.pos(this.posX + f - 1.0d + k, this.posY, 0.0d)
                .tex((i + f - 1.0d) * TEX_COORD_SCALE, j * TEX_COORD_SCALE)
                .color(this.ppatches_fontRendererBatching_currentRed, this.ppatches_fontRendererBatching_currentGreen, this.ppatches_fontRendererBatching_currentBlue, this.ppatches_fontRendererBatching_currentAlpha)
                .endVertex();
        bufferbuilder.pos(this.posX + k, this.posY, 0.0d)
                .tex(i * TEX_COORD_SCALE, j * TEX_COORD_SCALE)
                .color(this.ppatches_fontRendererBatching_currentRed, this.ppatches_fontRendererBatching_currentGreen, this.ppatches_fontRendererBatching_currentBlue, this.ppatches_fontRendererBatching_currentAlpha)
                .endVertex();
        return l;
    }

    //
    // renderUnicodeChar uses a separate set of textures, so we can't batch it together with the normal render output buffer
    //

    @Inject(method = "Lnet/minecraft/client/gui/FontRenderer;renderUnicodeChar(CZ)F",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/FontRenderer;loadGlyphTexture(I)V"),
            allow = 1, require = 1)
    private void ppatches_fontRendererBatching_renderUnicodeChar_markUnicodeDrawn(CallbackInfoReturnable<Float> ci) {
        this.ppatches_fontRendererBatching_drawnAnyUnicode = true;
    }

    @Inject(method = "Lnet/minecraft/client/gui/FontRenderer;renderUnicodeChar(CZ)F",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/GlStateManager;glBegin(I)V"),
            allow = 1, require = 1)
    private void ppatches_fontRendererBatching_renderUnicodeChar_backUpAndSetColor(CallbackInfoReturnable<Float> ci) {
        //set fixed-function vertex color (bypassing GlStateManager)
        GL11.glColor4f(this.ppatches_fontRendererBatching_currentRed, this.ppatches_fontRendererBatching_currentGreen, this.ppatches_fontRendererBatching_currentBlue, this.ppatches_fontRendererBatching_currentAlpha);
    }

    @Inject(method = "Lnet/minecraft/client/gui/FontRenderer;renderUnicodeChar(CZ)F",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/GlStateManager;glEnd()V", shift = At.Shift.AFTER),
            allow = 1, require = 1)
    private void ppatches_fontRendererBatching_renderUnicodeChar_restoreSavedColor(CallbackInfoReturnable<Float> ci) {
        //restore GlStateManager's saved color state
        GL11.glColor4f(GlStateManager.colorState.red, GlStateManager.colorState.green, GlStateManager.colorState.blue, GlStateManager.colorState.alpha);
    }
}
