package net.daporkchop.ppatches;

import net.minecraftforge.fml.common.Mod;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * @author DaPorkchop_
 */
@Mod(modid = PPatchesMod.MODID, name = PPatchesMod.NAME, useMetadata = true,
        acceptableRemoteVersions = "*",
        guiFactory = "net.daporkchop.ppatches.client.PPatchesGuiFactory")
public final class PPatchesMod {
    public static final String MODID = "ppatches";
    public static final String NAME = "PPatches";

    public static final Logger LOGGER = LogManager.getLogger(NAME);
}
