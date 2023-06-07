package net.daporkchop.ppatches;

import net.minecraftforge.fml.client.event.ConfigChangedEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * @author DaPorkchop_
 */
@Mod(modid = PPatchesMod.MODID, name = PPatchesMod.NAME, useMetadata = true,
        acceptableRemoteVersions = "*",
        guiFactory = "net.daporkchop.ppatches.client.PPatchesGuiFactory")
public final class PPatchesMod {
    public static final String MODID = "ppatches";
    public static final String NAME = "PPatches";

    @SubscribeEvent
    public void configChanged(ConfigChangedEvent.OnConfigChangedEvent event) {
        if (MODID.equals(event.getModID())) {
            PPatchesConfig.load();
        }
    }
}
