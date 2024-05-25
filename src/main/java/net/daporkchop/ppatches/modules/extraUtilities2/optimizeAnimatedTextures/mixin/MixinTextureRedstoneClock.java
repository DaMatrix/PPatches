package net.daporkchop.ppatches.modules.extraUtilities2.optimizeAnimatedTextures.mixin;

import com.google.common.base.Preconditions;
import net.daporkchop.ppatches.util.compat.optifine.OFCompatHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Pseudo;

/**
 * @author DaPorkchop_
 */
@Pseudo
@Mixin(targets = "com.rwtema.extrautils2.textures.TextureRedstoneClock", remap = false)
abstract class MixinTextureRedstoneClock extends TextureAtlasSprite {
    protected MixinTextureRedstoneClock(String spriteName) {
        super(spriteName);
    }

    /**
     * @author DaPorkchop_
     * @reason we're replacing the whole method
     */
    @Override
    @Overwrite
    public void updateAnimation() {
        if (OFCompatHelper.OPTIFINE && this.animationMetadata == null) {
            return;
        }
        boolean animationActive = OFCompatHelper.OPTIFINE && OFCompatHelper.SmartAnimations_checkAnimationActiveForSpriteAndUpdateAnimationActive(this);

        WorldClient world = Minecraft.getMinecraft().world;
        int size = this.framesTextureData.size();
        Preconditions.checkState(size == 20, "TextureRedstoneClock has %s frames?!?", size);
        int expectedFrame = world != null ? (int) (world.getTotalWorldTime() % 20L) : 0;

        int currentFrame = this.frameCounter;
        if (expectedFrame == currentFrame) {
            return;
        }

        int numSteps = expectedFrame - currentFrame;
        if (numSteps < 0) {
            numSteps += 20;
        }

        //this code seems to adjust the animation speed so that it doesn't jump around when the server is lagging and sends time updates
        if (numSteps <= 2) {
            currentFrame += numSteps;
        } else if (numSteps > (20 * 3) / 4) {
            return;
        } else {
            currentFrame += 4;
        }
        currentFrame = currentFrame % 20;

        if (!animationActive) {
            this.frameCounter = currentFrame;
            return;
        }

        //set frameCounter to the previous frame and tickCounter to the previous frame's time so that the vanilla animation update code will
        //actually switch to the requested frame
        int previousFrame = currentFrame == 0 ? 19 : currentFrame - 1;
        this.tickCounter = this.animationMetadata.getFrameTimeSingle(previousFrame);
        this.frameCounter = previousFrame;

        super.updateAnimation();
    }
}
