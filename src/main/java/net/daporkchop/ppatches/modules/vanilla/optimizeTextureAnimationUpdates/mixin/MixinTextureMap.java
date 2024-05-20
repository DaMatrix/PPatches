package net.daporkchop.ppatches.modules.vanilla.optimizeTextureAnimationUpdates.mixin;

import net.daporkchop.ppatches.PPatchesMod;
import net.daporkchop.ppatches.modules.vanilla.optimizeTextureAnimationUpdates.AnimationUpdater;
import net.daporkchop.ppatches.modules.vanilla.optimizeTextureAnimationUpdates.util.IMixinTextureAtlasSprite;
import net.daporkchop.ppatches.modules.vanilla.optimizeTextureAnimationUpdates.util.SpriteOrigin;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.Stitcher;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.texture.TextureUtil;
import net.minecraftforge.fml.common.ProgressManager;
import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

/**
 * @author DaPorkchop_
 */
@Mixin(TextureMap.class)
abstract class MixinTextureMap extends AbstractTexture {
    @Shadow
    private int mipmapLevels;

    @Shadow
    @Final
    private List<TextureAtlasSprite> listAnimatedSprites;

    @Shadow
    @Final
    private String basePath;

    @Unique
    private int ppatches_optimizeTextureAnimationUpdates_animationFramesAtlas;
    @Unique
    private AnimationUpdater ppatches_optimizeTextureAnimationUpdates_animationUpdater;

    /*@Inject(method = "finishLoading(Lnet/minecraft/client/renderer/texture/Stitcher;Lnet/minecraftforge/fml/common/ProgressManager$ProgressBar;II)V",
            at = @At("RETURN"),
            allow = 1, require = 1)
    private void ppatches_optimizeTextureAnimationUpdates_finishLoading_saveAtlas(Stitcher stitcher, ProgressManager.ProgressBar bar, int j, int k, CallbackInfo ci) {
        ppatches_optimizeTextureAnimationUpdates_dumpAtlasTexture(this.getGlTextureId(), stitcher.getCurrentWidth(), stitcher.getCurrentHeight(), this.mipmapLevels,
                Paths.get(".ppatches_textureAtlas", "primary"));
    }*/

    @Inject(method = "finishLoading(Lnet/minecraft/client/renderer/texture/Stitcher;Lnet/minecraftforge/fml/common/ProgressManager$ProgressBar;II)V",
            at = @At("RETURN"),
            allow = 1, require = 1)
    private void ppatches_optimizeTextureAnimationUpdates_finishLoading_constructAnimationFramesAtlas(Stitcher stitcher, ProgressManager.ProgressBar bar, int j, int k, CallbackInfo ci) {
        if (this.ppatches_optimizeTextureAnimationUpdates_animationUpdater != null) {
            this.ppatches_optimizeTextureAnimationUpdates_animationUpdater.close();
            this.ppatches_optimizeTextureAnimationUpdates_animationUpdater = null;
        }

        //find all the animated sprites
        List<TextureAtlasSprite> animatedSprites = this.listAnimatedSprites;

        if (animatedSprites.isEmpty()) {
            return;
        }

        //create a bunch of dummy TextureAtlasSprites, one for each frame of the animation. these are simply markers to be fed into the stitcher, and subsequently
        // extract the atlas locations for each frame from.
        List<TextureAtlasSprite[]> animatedSpriteFrames = new ArrayList<>(animatedSprites.size());
        for (TextureAtlasSprite sprite : animatedSprites) {
            int frames = sprite.getFrameCount();
            TextureAtlasSprite[] frameSprites = new TextureAtlasSprite[frames];
            for (int frame = 0; frame < frames; frame++) {
                TextureAtlasSprite frameSprite = new TextureAtlasSprite(sprite.getIconName() + "[frame #" + frame + ']');
                frameSprite.copyFrom(sprite);
                frameSprites[frame] = frameSprite;
            }

            animatedSpriteFrames.add(frameSprites);
        }

        //create a new stitcher and add all the textures
        int maxTextureSize = Minecraft.getGLMaximumTextureSize();
        Stitcher animationFramesStitcher = new Stitcher(maxTextureSize, maxTextureSize, 0, this.mipmapLevels);
        for (TextureAtlasSprite[] frameSprites : animatedSpriteFrames) {
            for (TextureAtlasSprite frameSprite : frameSprites) {
                animationFramesStitcher.addSprite(frameSprite);
            }
        }
        animationFramesStitcher.doStitch();
        animationFramesStitcher.getStichSlots(); //this will copy the atlas locations into each of the sprite instances

        //allocate the animation frames texture atlas
        PPatchesMod.LOGGER.info("Creating {}x{} {}-atlas for animation frames", animationFramesStitcher.getCurrentWidth(), animationFramesStitcher.getCurrentHeight(), this.basePath);
        if (this.ppatches_optimizeTextureAnimationUpdates_animationFramesAtlas < 0) {
            this.ppatches_optimizeTextureAnimationUpdates_animationFramesAtlas = TextureUtil.glGenTextures();
        }
        TextureUtil.allocateTextureImpl(this.ppatches_optimizeTextureAnimationUpdates_animationFramesAtlas,
                this.mipmapLevels, animationFramesStitcher.getCurrentWidth(), animationFramesStitcher.getCurrentHeight());

        this.ppatches_optimizeTextureAnimationUpdates_animationUpdater = new AnimationUpdater((TextureMap) (Object) this, animatedSprites,
                this.ppatches_optimizeTextureAnimationUpdates_animationFramesAtlas, this.getGlTextureId());

        //copy all of the frame bounds into the original sprite
        for (int spriteIndex = 0; spriteIndex < animatedSprites.size(); spriteIndex++) {
            TextureAtlasSprite sprite = animatedSprites.get(spriteIndex);
            TextureAtlasSprite[] frameSprites = animatedSpriteFrames.get(spriteIndex);

            SpriteOrigin[] frameBounds = new SpriteOrigin[frameSprites.length];
            for (int frame = 0; frame < frameSprites.length; frame++) {
                TextureAtlasSprite frameSprite = frameSprites[frame];

                TextureUtil.uploadTextureMipmap(sprite.getFrameTextureData(frame),
                        frameSprite.getIconWidth(), frameSprite.getIconHeight(), frameSprite.getOriginX(), frameSprite.getOriginY(), false, false);

                frameBounds[frame] = new SpriteOrigin(frameSprite.getOriginX(), frameSprite.getOriginY());
            }

            ((IMixinTextureAtlasSprite) sprite).ppatches_optimizeTextureAnimationUpdates_initAnimationData(frameBounds,
                    this.ppatches_optimizeTextureAnimationUpdates_animationUpdater);
        }

        /*ppatches_optimizeTextureAnimationUpdates_dumpAtlasTexture(this.ppatches_optimizeTextureAnimationUpdates_animationFramesAtlas,
                animationFramesStitcher.getCurrentWidth(), animationFramesStitcher.getCurrentHeight(), this.mipmapLevels,
                Paths.get(".ppatches_textureAtlas", "animation"));*/

        //restore the original texture binding
        GlStateManager.bindTexture(this.getGlTextureId());
    }

    @Inject(method = "updateAnimations()V",
            at = @At("TAIL"),
            allow = 1, require = 1)
    private void ppatches_optimizeTextureAnimationUpdates_updateAnimations_dispatchUpdater(CallbackInfo ci) {
        this.ppatches_optimizeTextureAnimationUpdates_animationUpdater.dispatch();
    }

    /**
     * This method serves as a dummy injection point; it will be silently discarded if another mixin targeting the same class adds the same override.
     */
    @Unique
    @Override
    public void deleteGlTexture() {
        super.deleteGlTexture();
    }

    @Dynamic
    @Inject(
            method = {
                    "deleteGlTexture()V",
                    "func_147631_c()V", //need to explicitly add the obfuscated method name, since the method doesn't exist in vanilla
            },
            at = @At(value = "HEAD"),
            allow = 1, require = 1)
    private void ppatches_optimizeTextureAnimationUpdates_deleteGlTexture_deleteAnimationFramesAtlas(CallbackInfo ci) {
        if (this.ppatches_optimizeTextureAnimationUpdates_animationUpdater != null) {
            this.ppatches_optimizeTextureAnimationUpdates_animationUpdater.close();
            this.ppatches_optimizeTextureAnimationUpdates_animationUpdater = null;
        }

        if (this.ppatches_optimizeTextureAnimationUpdates_animationFramesAtlas != -1) {
            GlStateManager.deleteTexture(this.ppatches_optimizeTextureAnimationUpdates_animationFramesAtlas);
            this.ppatches_optimizeTextureAnimationUpdates_animationFramesAtlas = -1;
        }
    }

    /*@SneakyThrows(IOException.class)
    @Unique
    private static void ppatches_optimizeTextureAnimationUpdates_dumpAtlasTexture(int textureId, int width, int height, int levels, Path dir) {
        Files.createDirectories(dir);

        for (int level = 0; level <= levels; level++) {
            int w = width >> level;
            int h = height >> level;

            IntBuffer buf = BufferUtils.createIntBuffer(w * h);
            ARBDirectStateAccess.glGetTextureImage(textureId, level, GL_BGRA, GL_UNSIGNED_INT_8_8_8_8_REV, buf);
            int[] arr = new int[w * h];
            buf.get(arr);
            TextureUtil.processPixelValues(arr, w, h);
            BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            img.setRGB(0, 0, w, h, arr, 0, w);

            ImageIO.write(img, "png", dir.resolve(level + ".png").toFile());
        }
    }*/
}
