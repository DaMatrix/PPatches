package net.daporkchop.ppatches.modules.vanilla.optimizeTextureAnimationUpdates;

import lombok.experimental.UtilityClass;
import net.daporkchop.ppatches.modules.vanilla.optimizeTextureAnimationUpdates.util.IMixinTextureMap;
import net.minecraftforge.client.event.TextureStitchEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * @author DaPorkchop_
 */
@UtilityClass
public class EventHandler {
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onTextureStichedPost(TextureStitchEvent.Post event) {
        ((IMixinTextureMap) event.getMap()).ppatches_optimizeTextureAnimationUpdates_constructAnimationFramesAtlas();
    }
}
