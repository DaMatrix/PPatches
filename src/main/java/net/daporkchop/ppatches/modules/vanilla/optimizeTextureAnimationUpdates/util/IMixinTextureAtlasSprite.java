package net.daporkchop.ppatches.modules.vanilla.optimizeTextureAnimationUpdates.util;

import net.daporkchop.ppatches.modules.vanilla.optimizeTextureAnimationUpdates.AnimationUpdater;

/**
 * @author DaPorkchop_
 */
public interface IMixinTextureAtlasSprite {
    void ppatches_optimizeTextureAnimationUpdates_initAnimationData(SpriteOrigin[] spriteBounds, AnimationUpdater animationUpdater);
}
