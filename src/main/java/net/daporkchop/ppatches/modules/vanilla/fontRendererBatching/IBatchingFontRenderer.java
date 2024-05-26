package net.daporkchop.ppatches.modules.vanilla.fontRendererBatching;

/**
 * This interface is implemented by {@link net.minecraft.client.gui.FontRenderer} if {@code vanilla.fontRendererBatching} is enabled.
 *
 * @author DaPorkchop_
 */
public interface IBatchingFontRenderer {
    /**
     * Begins rendering batched text.
     * <p>
     * Once this is called, the only allowed rendering operations are rendering text via this {@link net.minecraft.client.gui.FontRenderer}.
     * <p>
     * After calling this method, the font rendering batch must be explicitly finished using {@link #ppatches_fontRendererBatching_endBatchedRendering()}.
     */
    void ppatches_fontRendererBatching_beginBatchedRendering();

    /**
     * Explicitly completes a previously started font rendering batch.
     */
    void ppatches_fontRendererBatching_endBatchedRendering();
}
