package net.daporkchop.ppatches.modules.vanilla.optimizeTextureAnimationUpdates;

import lombok.val;
import net.daporkchop.ppatches.PPatchesMod;
import net.daporkchop.ppatches.modules.vanilla.optimizeTextureAnimationUpdates.util.IMixinTextureMap;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraftforge.client.event.TextureStitchEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.lwjgl.opengl.GLContext;

import java.util.Iterator;

/**
 * @author DaPorkchop_
 */
public abstract class EventHandler {
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onTextureStichedPost(TextureStitchEvent.Post event) {
        for (Iterator<TextureAtlasSprite> itr = event.getMap().listAnimatedSprites.iterator(); itr.hasNext(); ) {
            TextureAtlasSprite sprite = itr.next();
            if (sprite.animationMetadata == null) {
                itr.remove();
            } else if (sprite.animationMetadata.getFrameCount() <= 1) {
                PPatchesMod.LOGGER.info("Disabling texture animation for {} as it only has {} frames",
                        sprite.getIconName(), sprite.animationMetadata.getFrameCount());
                itr.remove();
            }
        }

        if (!AnimationUpdater.isSupported(GLContext.getCapabilities())) {
            val log = PPatchesMod.LOGGER;
            log.fatal("****************************************");
            log.fatal("* Required OpenGL features are not supported! This module will be disabled.");
            log.fatal("****************************************");
            return;
        }

        ((IMixinTextureMap) event.getMap()).ppatches_optimizeTextureAnimationUpdates_constructAnimationFramesAtlas();
    }
}
