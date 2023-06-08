package net.daporkchop.ppatches.modules.vanilla.optimizeTessellatorDraw;

import net.daporkchop.ppatches.PPatchesConfig;
import net.daporkchop.ppatches.PPatchesLoadingPlugin;
import net.daporkchop.ppatches.PPatchesMod;
import net.minecraftforge.common.config.Config;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * @author DaPorkchop_
 */
public class ModuleConfigOptimizeTessellatorDraw extends PPatchesConfig.ModuleConfigBase {
    @Config.Comment({
            "Selects the technique to use for uploading vertex data to the GPU.",
            "Which mode is the fastest depends on your GPU driver. Testing indicates that NVIDIA devices work best with STAGING_BUFFER, while AMD devices get better"
            + " performance when using ORPHAN_BUFFER. If you are seeing visual artifacts after enabling this module, try switching to another render mode.",
    })
    public Mode mode = Mode.STAGING_BUFFER;

    @Config.Comment({
            "If the mode is STAGING_BUFFER, this option controls the staging buffer's capacity in kilobytes.",
            "Using a value that is very small can hurt performance, while too large will simply be a waste of memory. The default capacity should be fine for most users.",
    })
    @Config.RangeInt(min = 1)
    public int stagingBufferCapacity = 16 << 10;

    @Config.Comment({
            "If the mode is STAGING_BUFFER, invalidate the staging buffer contents should be kept in client memory (i.e. RAM instead of VRAM).",
            "Whether or not this will give a performance increase depends on your GPU driver. NVIDIA devices seem to benefit from having it enabled.",
    })
    public boolean stagingBufferClientStorage = true;

    @Config.Comment({
            "If the mode is STAGING_BUFFER, invalidate the staging buffer contents every time the staging buffer is filled up and reset.",
            "Disabling this may cause visual artifacts, however not all GPU vendors may support it.",
    })
    public boolean stagingBufferInvalidateOnReset = true;

    @Config.Comment({
            "If the mode is STAGING_BUFFER, enabling this option will print a message to the log every time the staging buffer is filled up.",
            "This is only intended for debugging and for power users who want to tune the staging buffer capacity.",
    })
    public boolean stagingBufferLogOnReset = false;

    @Override
    @SideOnly(Side.CLIENT)
    public void loadFromConfig(Configuration configuration, String category, boolean init) {
        super.loadFromConfig(configuration, category, init);

        if (PPatchesLoadingPlugin.isStarted && configuration.getCategory(category).hasChanged()) {
            PPatchesMod.LOGGER.info("vanilla.optimizeTessellatorDraw config changed, reloading...");
            VAOWorldVertexBufferUploader.ALL_INSTANCES.forEach(VAOWorldVertexBufferUploader::onConfigReload);
        }
    }

    public enum Mode {
        @Config.Comment({
                "Copies data directly to OpenGL using a persistently mapped buffer. Note that this requires driver support for either OpenGL 4.4 or ARB_buffer_storage!",
        })
        STAGING_BUFFER,
        @Config.Comment({
                "Uses glBufferData to upload data to OpenGL, orphaning the previous buffer contents in the process. This should be supported everywhere, but may result in"
                + " worse performance than STAGING_BUFFER on some drivers.",
        })
        ORPHAN_BUFFER,
    }
}
