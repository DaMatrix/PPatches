package net.daporkchop.ppatches.modules.vanilla.optimizeTextureAnimationUpdates.mixin;

import net.daporkchop.ppatches.modules.vanilla.optimizeTextureAnimationUpdates.AnimationUpdater;
import net.daporkchop.ppatches.modules.vanilla.optimizeTextureAnimationUpdates.UpdateItemList;
import net.daporkchop.ppatches.modules.vanilla.optimizeTextureAnimationUpdates.util.IMixinTextureAtlasSprite;
import net.daporkchop.ppatches.modules.vanilla.optimizeTextureAnimationUpdates.util.SpriteOrigin;
import net.daporkchop.ppatches.util.mixin.ext.AlwaysCancels;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.data.AnimationMetadataSection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

/**
 * @author DaPorkchop_
 */
@Mixin(TextureAtlasSprite.class)
abstract class MixinTextureAtlasSprite implements IMixinTextureAtlasSprite {
    @Shadow
    public AnimationMetadataSection animationMetadata;
    @Shadow
    protected int frameCounter;
    @Shadow
    protected int originX;
    @Shadow
    protected int originY;

    @Unique
    private SpriteOrigin[] ppatches_optimizeTextureAnimationUpdates_spriteBounds;
    @Unique
    private UpdateItemList ppatches_optimizeTextureAnimationUpdates_animationUpdateList;

    @Override
    public final void ppatches_optimizeTextureAnimationUpdates_initAnimationData(SpriteOrigin[] spriteBounds, AnimationUpdater animationUpdater) {
        this.ppatches_optimizeTextureAnimationUpdates_spriteBounds = spriteBounds;
        this.ppatches_optimizeTextureAnimationUpdates_animationUpdateList = animationUpdater.findUpdateList((TextureAtlasSprite) (Object) this);
    }

    @Inject(method = "clearFramesTextureData()V",
            at = @At("HEAD"),
            allow = 1, require = 1)
    private void ppatches_optimizeTextureAnimationUpdates_clearFramesTextureData_resetAnimationData(CallbackInfo ci) {
        this.ppatches_optimizeTextureAnimationUpdates_resetAnimationData();
    }

    @Inject(method = "setFramesTextureData(Ljava/util/List;)V",
            at = @At("HEAD"),
            allow = 1, require = 1)
    private void ppatches_optimizeTextureAnimationUpdates_setFramesTextureData_resetAnimationData(CallbackInfo ci) {
        this.ppatches_optimizeTextureAnimationUpdates_resetAnimationData();
    }

    @Unique
    private void ppatches_optimizeTextureAnimationUpdates_resetAnimationData() {
        this.ppatches_optimizeTextureAnimationUpdates_spriteBounds = null;
        this.ppatches_optimizeTextureAnimationUpdates_animationUpdateList = null;
    }

    @Redirect(method = "updateAnimation()V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/texture/TextureUtil;uploadTextureMipmap([[IIIIIZZ)V"),
            allow = 1, require = 1)
    private void ppatches_optimizeTextureAnimationUpdates_updateAnimation_addToUpdateList(
            int[][] frameTextureData, int width, int height, int originX, int originY, boolean doBlur, boolean doClamp) {
        int frameIndex = this.animationMetadata.getFrameIndex(this.frameCounter);
        SpriteOrigin frameBounds = this.ppatches_optimizeTextureAnimationUpdates_spriteBounds[frameIndex];
        this.ppatches_optimizeTextureAnimationUpdates_animationUpdateList.add(
                frameBounds.originX, frameBounds.originY,
                originX, originY);
    }

    @Inject(method = "updateAnimationInterpolated()V",
            at = @At(value = "INVOKE",
                    target = "Ljava/util/List;get(I)Ljava/lang/Object;",
                    ordinal = 0),
            cancellable = true,
            locals = LocalCapture.CAPTURE_FAILHARD,
            allow = 1, require = 1)
    @AlwaysCancels
    private void ppatches_optimizeTextureAnimationUpdates_updateAnimationInterpolated_addToUpdateList(CallbackInfo ci, double factor, int prevFrameIndex, int frames, int nextFrameIndex) {
        SpriteOrigin prevFrameBounds = this.ppatches_optimizeTextureAnimationUpdates_spriteBounds[prevFrameIndex];
        SpriteOrigin nextFrameBounds = this.ppatches_optimizeTextureAnimationUpdates_spriteBounds[nextFrameIndex];
        this.ppatches_optimizeTextureAnimationUpdates_animationUpdateList.addInterpolate(
                prevFrameBounds.originX, prevFrameBounds.originY,
                nextFrameBounds.originX, nextFrameBounds.originY,
                this.originX, this.originY,
                (float) factor);
        ci.cancel();
    }

    /*@Inject(method = "loadSpriteFrames(Lnet/minecraft/client/resources/IResource;I)V",
            at = @At("RETURN"),
            allow = 1, require = 1)
    private void ppatches_optimizeTextureAnimationUpdates_loadSpriteFrames_makeEverythingAnimated(CallbackInfo ci) {
        if (this.framesTextureData.size() == 1) {
            final int FRAMES = 3;

            for (int frame = 1; frame < FRAMES; frame++) {
                int[][] nextFrame = this.framesTextureData.get(0).clone();

                double factor = 1.0d - (frame / (double) FRAMES);
                for (int i = 0; i < nextFrame.length; i++) {
                    if (nextFrame[i] != null) {
                        int[] arr = nextFrame[i] = nextFrame[i].clone();
                        for (int j = 0; j < arr.length; j++) {
                            int c0 = arr[j];
                            int c1 = 0xFF000000;
                            int l1 = this.interpolateColor(factor, c0 >> 16 & 255, c1 >> 16 & 255);
                            int i2 = this.interpolateColor(factor, c0 >> 8 & 255, c1 >> 8 & 255);
                            int j2 = this.interpolateColor(factor, c0 & 255, c1 & 255);
                            arr[j] = c0 & -16777216 | l1 << 16 | i2 << 8 | j2;
                        }
                    }
                }

                this.framesTextureData.add(nextFrame);
            }

            this.animationMetadata = new AnimationMetadataSection(
                    IntStream.range(0, FRAMES)
                            .mapToObj(frame -> new AnimationFrame(frame, -1))
                            .collect(Collectors.toList()),
                    this.width,
                    this.height,
                    2,
                    false);
        }
    }*/

    /*@Inject(method = "loadSpriteFrames(Lnet/minecraft/client/resources/IResource;I)V",
            at = @At("RETURN"),
            allow = 1, require = 1)
    private void ppatches_optimizeTextureAnimationUpdates_loadSpriteFrames_disableInterpolatedTextures(CallbackInfo ci) {
        //make interpolated textures not be interpolated
        if (this.animationMetadata != null && this.animationMetadata.isInterpolate()) {
            this.animationMetadata = new AnimationMetadataSection(
                    ((ATAnimationMetadataSection) this.animationMetadata).getAnimationFrames(),
                    this.animationMetadata.getFrameWidth(),
                    this.animationMetadata.getFrameHeight(),
                    this.animationMetadata.getFrameTime(),
                    false);
        }
    }*/

    /*@Inject(method = "loadSpriteFrames(Lnet/minecraft/client/resources/IResource;I)V",
            at = @At(value = "FIELD",
                    target = "Lnet/minecraft/client/renderer/texture/TextureAtlasSprite;animationMetadata:Lnet/minecraft/client/resources/data/AnimationMetadataSection;",
                    opcode = Opcodes.PUTFIELD,
                    shift = At.Shift.AFTER),
            allow = 2, require = 2)
    private void ppatches_optimizeTextureAnimationUpdates_loadSpriteFrames_precomputeInterpolatedAnimations(CallbackInfo ci) {
        if (this.animationMetadata == null || !this.animationMetadata.isInterpolate()) { //nothing to do
            return;
        }

        AnimationMetadataSection animationMetadata = this.animationMetadata;
        List<AnimationFrame> animationFrames = ((ATAnimationMetadataSection) animationMetadata).getAnimationFrames();
        List<int[][]> framesData = this.framesTextureData;

        Preconditions.checkState(!animationFrames.isEmpty(), "%s: animation metadata contains 0 frames", this.getIconName());

        AnimatedTextureBuilder animatedTextureBuilder = new AnimatedTextureBuilder(framesData.get(animationMetadata.getFrameIndex(0)));

        int tickCounter = 0;
        int frameCounter = 0;
        do {
            int[][] nextFrameData;

            tickCounter++;
            if (tickCounter >= animationMetadata.getFrameTimeSingle(frameCounter)) {
                int prevFrameIndex = animationMetadata.getFrameIndex(frameCounter);
                int totalFrameCount = animationMetadata.getFrameCount() == 0 ? framesData.size() : animationMetadata.getFrameCount();
                frameCounter = (frameCounter + 1) % totalFrameCount;
                tickCounter = 0;
                int nextFrameIndex = animationMetadata.getFrameIndex(frameCounter);

                nextFrameData = framesData.get(nextFrameIndex);
            } else {
                double ratio = 1.0d - tickCounter / (double) animationMetadata.getFrameTimeSingle(frameCounter);
                int prevFrameIndex = animationMetadata.getFrameIndex(frameCounter);
                int totalFrameCount = animationMetadata.getFrameCount() == 0 ? framesData.size() : animationMetadata.getFrameCount();
                int nextFrameIndex = animationMetadata.getFrameIndex((frameCounter + 1) % totalFrameCount);

                int[][] prevFrameData = framesData.get(prevFrameIndex);
                nextFrameData = framesData.get(nextFrameIndex);
                if (prevFrameIndex != nextFrameIndex) {
                    nextFrameData = this.interpolateFrame(prevFrameData, nextFrameData, ratio);
                }
            }

            animatedTextureBuilder.addFrameWithData(nextFrameData);
        } while (tickCounter != 0 || frameCounter != 0);

        animatedTextureBuilder.finish();

        this.animationMetadata = new AnimationMetadataSection(
                animatedTextureBuilder.getFrames(),
                animationMetadata.getFrameWidth(), animationMetadata.getFrameHeight(), animationMetadata.getFrameTime(),
                false);
        this.framesTextureData.clear();
        this.framesTextureData.addAll(animatedTextureBuilder.getFramesData());
    }

    @Unique
    private int[][] interpolateFrame(int[][] from1, int[][] from2, double ratio) {
        Preconditions.checkArgument(from1.length == from2.length);
        int[][] to = new int[from1.length][];
        for (int i = 0; i < from1.length; i++) {
            if (from1[i] != null) {
                to[i] = this.interpolateFrame(from1[i], from2[i], ratio);
            }
        }
        return to;
    }

    @Unique
    private int[] interpolateFrame(int[] from1, int[] from2, double ratio) {
        Preconditions.checkArgument(from1.length == from2.length);
        int[] to = new int[from1.length];
        for (int i = 0; i < from1.length; ++i) {
            int color1 = from1[i];
            int color2 = from2[i];
            int colorRed = this.interpolateColor(ratio, color1 >> 16 & 0xFF, color2 >> 16 & 0xFF);
            int colorGreen = this.interpolateColor(ratio, color1 >> 8 & 0xFF, color2 >> 8 & 0xFF);
            int colorBlue = this.interpolateColor(ratio, color1 & 0xFF, color2 & 0xFF);
            to[i] = color1 & 0xFF000000 | colorRed << 16 | colorGreen << 8 | colorBlue;
        }
        return to;
    }

    @Unique
    private int[][] cloneFrame(int[][] from) {
        int[][] to = new int[from.length][];
        for (int i = 0; i < from.length; i++) {
            to[i] = from[i].clone();
        }
        return to;
    }*/
}
