package net.daporkchop.ppatches.modules.foamFix;

import net.daporkchop.ppatches.PPatchesMod;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.data.AnimationFrame;
import net.minecraft.client.resources.data.AnimationMetadataSection;
import net.minecraftforge.client.event.TextureStitchEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.lang.reflect.Field;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * @author DaPorkchop_
 */
public abstract class FoamFixLogSlowAnimationsEventHandler {
    @SubscribeEvent
    public static void onTextureStichedPost(TextureStitchEvent.Post event) throws ReflectiveOperationException {
        Class<?> FastTextureAtlasSprite = Class.forName("pl.asie.foamfix.client.FastTextureAtlasSprite");

        Field FoamFixShared_config = Class.forName("pl.asie.foamfix.shared.FoamFixShared").getField("config");
        int txCacheAnimationMaxFrames = FoamFixShared_config.getType().getField("txCacheAnimationMaxFrames").getInt(FoamFixShared_config.get(null));
        boolean shouldFasterAnimation = Class.forName("pl.asie.foamfix.FoamFix").getField("shouldFasterAnimation").getBoolean(null);

        for (TextureAtlasSprite sprite : event.getMap().listAnimatedSprites) {
            if (!FastTextureAtlasSprite.isInstance(sprite)) {
                PPatchesMod.LOGGER.warn("FoamFix won't use faster animations on texture {}! Not an instance of FastTextureAtlasSprite: {}",
                        sprite.getIconName(), sprite.getClass());
                continue;
            }

            int size = sprite.getFrameCount();
            boolean interpolate = sprite.animationMetadata.isInterpolate();

            if (false && interpolate) {
                PPatchesMod.LOGGER.info("Disabling animated texture interpolation for {}", sprite.getIconName());
                sprite.animationMetadata = new AnimationMetadataSection(
                        IntStream.range(0, sprite.animationMetadata.getFrameCount())
                                .mapToObj(frame -> new AnimationFrame(sprite.animationMetadata.getFrameIndex(frame), sprite.animationMetadata.frameHasTime(frame) ? sprite.animationMetadata.getFrameTimeSingle(frame) : -1))
                                .collect(Collectors.toList()),
                        sprite.animationMetadata.getFrameWidth(),
                        sprite.animationMetadata.getFrameHeight(),
                        sprite.animationMetadata.getFrameTime(),
                        false);
                interpolate = false;
            }

            if (size <= 1 || size > txCacheAnimationMaxFrames || !shouldFasterAnimation || interpolate) {
                PPatchesMod.LOGGER.warn("FoamFix won't use faster animations on texture {}! size={}, txCacheAnimationMaxFrames={}, shouldFasterAnimation={}, interpolate={}",
                        sprite.getIconName(), size, txCacheAnimationMaxFrames, shouldFasterAnimation, interpolate);
                continue;
            }
        }
    }
}
