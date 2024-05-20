package net.daporkchop.ppatches;

import lombok.SneakyThrows;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.ProgressManager;
import net.minecraftforge.fml.common.event.FMLConstructionEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

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

    @Mod.EventHandler
    @SneakyThrows
    public void construction(FMLConstructionEvent event) {
        //register event handlers for all enabled modules
        List<String> eventHandlerClasses = new ArrayList<>();
        for (PPatchesConfig.ModuleConfigBase module : PPatchesConfig.listModules().values()) {
            if (module.isEnabled()) {
                eventHandlerClasses.addAll(Arrays.asList(module.descriptor.eventHandlerClass()));
            }
        }

        ProgressManager.ProgressBar bar = ProgressManager.push("Register event handlers", eventHandlerClasses.size());
        for (String eventHandlerClass : eventHandlerClasses) {
            bar.step(eventHandlerClass);
            MinecraftForge.EVENT_BUS.register(Class.forName(eventHandlerClass));
        }
        ProgressManager.pop(bar);
    }
}
