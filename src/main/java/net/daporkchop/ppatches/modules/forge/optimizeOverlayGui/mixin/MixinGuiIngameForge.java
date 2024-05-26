package net.daporkchop.ppatches.modules.forge.optimizeOverlayGui.mixin;

import net.daporkchop.ppatches.modules.vanilla.fontRendererBatching.IBatchingFontRenderer;
import net.daporkchop.ppatches.util.client.render.RenderUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiIngame;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraftforge.client.GuiIngameForge;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Slice;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

import java.util.ArrayList;

/**
 * @author DaPorkchop_
 */
@Mixin(value = GuiIngameForge.class, remap = false)
abstract class MixinGuiIngameForge extends GuiIngame {
    @Shadow
    private FontRenderer fontrenderer;

    protected MixinGuiIngameForge(Minecraft mcIn) {
        super(mcIn);
    }

    // baseline: 730-740
    // full: 830-840

    @Inject(method = "renderHUDText(II)V",
            slice = @Slice(
                    from = @At(value = "INVOKE:ONE",
                            target = "Lnet/minecraftforge/fml/common/eventhandler/EventBus;post(Lnet/minecraftforge/fml/common/eventhandler/Event;)Z"),
                    to = @At(value = "INVOKE:FIRST",
                            target = "Ljava/util/ArrayList;iterator()Ljava/util/Iterator;")),
            at = @At(value = "CONSTANT",
                    args = "intValue=2"),
            locals = LocalCapture.CAPTURE_FAILHARD,
            allow = 1, require = 1)
    private void ppatches_optimizeOverlayGui_renderHUDText_renderBackgroundBatched(int width, int height, CallbackInfo ci, ArrayList<String> listL, ArrayList<String> listR) {
        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();

        final int color = -1873784752;
        final float a = (float) (color >> 24 & 255) / 255.0F;
        final float r = (float) (color >> 16 & 255) / 255.0F;
        final float g = (float) (color >> 8 & 255) / 255.0F;
        final float b = (float) (color & 255) / 255.0F;

        GlStateManager.enableBlend();
        GlStateManager.disableTexture2D();
        GlStateManager.tryBlendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA, GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
        GlStateManager.color(r, g, b, a);
        buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION);

        final int FONT_HEIGHT = this.fontrenderer.FONT_HEIGHT;

        int top = 2;
        for (String msg : listL) {
            if (msg != null) {
                RenderUtils.drawRect(buffer, 1, top - 1, 2 + this.fontrenderer.getStringWidth(msg) + 1, top + FONT_HEIGHT - 1);
                top += FONT_HEIGHT;
            }
        }

        top = 2;
        for (String msg : listR) {
            if (msg != null) {
                int w = this.fontrenderer.getStringWidth(msg);
                RenderUtils.drawRect(buffer, width - 2 - w - 1, top - 1, width - 1, top + FONT_HEIGHT - 1);
                top += FONT_HEIGHT;
            }
        }

        tessellator.draw();
        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();
    }

    @Inject(method = "renderHUDText(II)V",
            slice = @Slice(
                    from = @At(value = "INVOKE:ONE",
                            target = "Lnet/minecraftforge/fml/common/eventhandler/EventBus;post(Lnet/minecraftforge/fml/common/eventhandler/Event;)Z"),
                    to = @At(value = "INVOKE:FIRST",
                            target = "Ljava/util/ArrayList;iterator()Ljava/util/Iterator;")),
            at = @At(value = "CONSTANT",
                    args = "intValue=2",
                    shift = At.Shift.AFTER),
            locals = LocalCapture.CAPTURE_FAILHARD,
            allow = 1, require = 1)
    private void ppatches_optimizeOverlayGui_renderHUDText_renderTextBatched(int width, int height, CallbackInfo ci, ArrayList<String> listL, ArrayList<String> listR) {
        if (!(this.fontrenderer instanceof IBatchingFontRenderer)) {
            return;
        }

        ((IBatchingFontRenderer) this.fontrenderer).ppatches_fontRendererBatching_beginBatchedRendering();
        try {
            final int FONT_HEIGHT = this.fontrenderer.FONT_HEIGHT;

            int top = 2;
            for (String msg : listL) {
                if (msg != null) {
                    this.fontrenderer.drawString(msg, 2, top, 14737632);
                    top += FONT_HEIGHT;
                }
            }

            top = 2;
            for (String msg : listR) {
                if (msg != null) {
                    this.fontrenderer.drawString(msg, width - 2 - this.fontrenderer.getStringWidth(msg), top, 14737632);
                    top += FONT_HEIGHT;
                }
            }
        } finally {
            ((IBatchingFontRenderer) this.fontrenderer).ppatches_fontRendererBatching_endBatchedRendering();
        }

        listL.clear();
        listR.clear();
    }

    @Redirect(method = "renderHUDText(II)V",
            slice = @Slice(
                    from = @At(value = "INVOKE:ONE",
                            target = "Lnet/minecraftforge/fml/common/eventhandler/EventBus;post(Lnet/minecraftforge/fml/common/eventhandler/Event;)Z"),
                    to = @At(value = "INVOKE:ONE",
                            target = "Lnet/minecraft/profiler/Profiler;endSection()V", remap = true)),
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraftforge/client/GuiIngameForge;drawRect(IIIII)V", remap = true),
            allow = 2, require = 2)
    private void ppatches_optimizeOverlayGui_renderHUDText_disableRect(int left, int top, int right, int bottom, int color) {
        //no-op
    }

    /*@Redirect(method = "renderHUDText(II)V",
            slice = @Slice(
                    from = @At(value = "INVOKE:ONE",
                            target = "Lnet/minecraftforge/fml/common/eventhandler/EventBus;post(Lnet/minecraftforge/fml/common/eventhandler/Event;)Z"),
                    to = @At(value = "INVOKE:ONE",
                            target = "Lnet/minecraft/profiler/Profiler;endSection()V", remap = true)),
            at = @At(value = "INVOKE",
                    target = "Ljava/util/ArrayList;iterator()Ljava/util/Iterator;"),
            allow = 2, require = 2)
    private Iterator<String> ppatches_optimizeOverlayGui_renderHUDText_renderDebugOverlayBatched(ArrayList<String> list, int width, int height) {
        return Collections.emptyIterator();
    }*/
}
