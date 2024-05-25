package net.daporkchop.ppatches.modules.vanilla.optimizeTextureAnimationUpdates;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import lombok.SneakyThrows;
import net.daporkchop.ppatches.PPatchesMod;
import net.daporkchop.ppatches.modules.vanilla.optimizeTextureAnimationUpdates.util.SpriteSize;
import net.daporkchop.ppatches.util.client.render.Shader;
import net.daporkchop.ppatches.util.client.render.ShaderProgram;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.util.math.MathHelper;
import org.apache.commons.io.IOUtils;
import org.lwjgl.opengl.ARBComputeShader;
import org.lwjgl.opengl.ARBMultiBind;
import org.lwjgl.opengl.ARBShaderImageLoadStore;
import org.lwjgl.opengl.ARBShaderStorageBufferObject;
import org.lwjgl.opengl.ARBUniformBufferObject;
import org.lwjgl.opengl.ContextCapabilities;
import org.lwjgl.opengl.GLContext;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.lwjgl.opengl.GL11.GL_RGBA8;
import static org.lwjgl.opengl.GL11.glGetInteger;
import static org.lwjgl.opengl.GL15.GL_READ_ONLY;
import static org.lwjgl.opengl.GL15.GL_WRITE_ONLY;
import static org.lwjgl.opengl.GL20.glUniform1i;

/**
 * @author DaPorkchop_
 */
public final class AnimationUpdater implements AutoCloseable {
    public static boolean isSupported(ContextCapabilities capabilities) {
        return UpdateItemList.isSupported(capabilities)
                & (capabilities.OpenGL43 | capabilities.GL_ARB_compute_shader)
                & (capabilities.OpenGL42 | capabilities.GL_ARB_shader_image_load_store)
                & (capabilities.OpenGL43 | capabilities.GL_ARB_shader_storage_buffer_object)
                & (capabilities.OpenGL31 | capabilities.GL_ARB_uniform_buffer_object)
                & capabilities.OpenGL43; //required for compute shaders, i'm too lazy to deal with enabling GLSL extensions as needed
    }

    public final int baseResolution = 16; //prevent inlining
    public final int mipmapLevels;

    private final int maxTextureImageUnits;

    private List<ShaderProgram> updateShaders;
    private final ImmutableMap<SpriteSize, UpdateItemList> updateLists;

    private final int srcTexture;
    private final int dstTexture;

    public AnimationUpdater(TextureMap textureMap, List<TextureAtlasSprite> animatedSprites, int srcTexture, int dstTexture) {
        this.maxTextureImageUnits = glGetInteger(ARBComputeShader.GL_MAX_COMPUTE_IMAGE_UNIFORMS);

        this.srcTexture = srcTexture;
        this.dstTexture = dstTexture;

        this.mipmapLevels = textureMap.getMipmapLevels() + 1;

        this.updateLists = ImmutableMap.copyOf(animatedSprites.stream()
                .collect(Collectors.groupingBy(
                        this::spriteToSize,
                        Collectors.collectingAndThen(Collectors.counting(), count -> new UpdateItemList(Math.toIntExact(count))))));

        this.updateShaders = this.compileShaders();
    }

    private SpriteSize spriteToSize(TextureAtlasSprite sprite) {
        int width = MathHelper.roundUp(sprite.getIconWidth(), this.baseResolution);
        int height = MathHelper.roundUp(sprite.getIconHeight(), this.baseResolution);
        return new SpriteSize(width, height);
    }

    public UpdateItemList findUpdateList(TextureAtlasSprite sprite) {
        return this.updateLists.get(this.spriteToSize(sprite));
    }

    @SneakyThrows
    private List<ShaderProgram> compileShaders() {
        List<ShaderProgram> updateShaders = new ArrayList<>();
        try {
            int mipmapsPerShader = this.maxTextureImageUnits >> 1;
            for (int baseMipmap = 0; baseMipmap < this.mipmapLevels; ) {
                int levelsThisShader = Math.min(mipmapsPerShader, this.mipmapLevels - baseMipmap);
                updateShaders.add(compileShader(baseMipmap, levelsThisShader));
                baseMipmap += levelsThisShader;
            }

            return updateShaders;
        } catch (Throwable t) {
            updateShaders.forEach(ShaderProgram::close);
            throw t;
        }
    }

    @SneakyThrows(IOException.class)
    private static ShaderProgram compileShader(int baseMipmap, int mipmapLevels) {
        PPatchesMod.LOGGER.info("compiling animation update shader for levels {} to {}", baseMipmap, baseMipmap + mipmapLevels);

        String versionHeader = "#version 430";

        //compile the update shader
        String shaderSource;
        try (InputStream stream = AnimationUpdater.class.getResourceAsStream("texture_update.comp")) {
            shaderSource = IOUtils.toString(stream, StandardCharsets.UTF_8);
        }

        ShaderProgram updateShader;
        try (Shader shader = new Shader(ARBComputeShader.GL_COMPUTE_SHADER, ImmutableList.of(
                versionHeader,
                "#define BASE_MIPMAP (" + baseMipmap + ')',
                "#define MIPMAP_LEVELS (" + mipmapLevels + ')',
                shaderSource))) {
            updateShader = new ShaderProgram(ImmutableList.of(shader));
        }

        //configure the image binding locations in the update shader
        updateShader.bind(() -> {
            for (int level = 0; level < mipmapLevels; level++) {
                glUniform1i(updateShader.uniformLocation("u_srcTexture[" + level + ']'), level);
                glUniform1i(updateShader.uniformLocation("u_dstTexture[" + level + ']'), level + mipmapLevels);
            }
        });

        return updateShader;
    }

    //private boolean sectionDown;

    public void dispatch() {
        /*if (Keyboard.isKeyDown(Keyboard.KEY_F1)) {
            if (!this.sectionDown) {
                this.sectionDown = true;

                try {
                    List<ShaderProgram> updateShaders = this.compileShaders();
                    PPatchesMod.LOGGER.info("shader reload successful");
                    this.updateShaders.forEach(ShaderProgram::close);
                    this.updateShaders = updateShaders;
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        } else {
            this.sectionDown = false;
        }*/

        if (this.updateLists.values().stream().mapToInt(UpdateItemList::size).sum() == 0) {
            return;
        }

        this.updateLists.values().forEach(UpdateItemList::flush);

        int mipmapsPerShader = this.maxTextureImageUnits >> 1;
        for (int i = 0, baseMipmap = 0; baseMipmap < this.mipmapLevels; i++) {
            int levelsThisShader = Math.min(mipmapsPerShader, this.mipmapLevels - baseMipmap);

            //bind source and destination images
            //TODO: this could use multibind, but it would require us to create texture views which would require the
            // backing textures to use immutable storage...
            for (int level = baseMipmap; level < baseMipmap + levelsThisShader; level++) {
                ARBShaderImageLoadStore.glBindImageTexture(level - baseMipmap,
                        this.srcTexture, level, false, 0, GL_READ_ONLY, GL_RGBA8);
                ARBShaderImageLoadStore.glBindImageTexture(level - baseMipmap + levelsThisShader,
                        this.dstTexture, level, false, 0, GL_WRITE_ONLY, GL_RGBA8);
            }

            this.updateShaders.get(i).bind(() -> {
                for (Map.Entry<SpriteSize, UpdateItemList> entry : this.updateLists.entrySet()) {
                    SpriteSize size = entry.getKey();
                    UpdateItemList list = entry.getValue();
                    if (list.size() == 0) {
                        continue;
                    }

                    //TODO: what if the list contains more entries than the maximum dispatch size?
                    //TODO: during the high base mipmap iteration(s), this will end up submitting more jobs than necessary along the Y and Z axes, resulting in
                    //      a lot of work groups exiting without doing anything
                    ARBUniformBufferObject.glBindBufferBase(ARBShaderStorageBufferObject.GL_SHADER_STORAGE_BUFFER, 0, list.gpuBuffer);
                    ARBComputeShader.glDispatchCompute(list.size(), size.width / this.baseResolution, size.height / this.baseResolution);
                }
            });

            baseMipmap += levelsThisShader;
        }

        this.updateLists.values().forEach(UpdateItemList::clear);

        //reset bindings to 0
        ARBUniformBufferObject.glBindBufferBase(ARBShaderStorageBufferObject.GL_SHADER_STORAGE_BUFFER, 0, 0);
        ContextCapabilities capabilities = GLContext.getCapabilities();
        if (capabilities.OpenGL44 | capabilities.GL_ARB_multi_bind) {
            ARBMultiBind.glBindImageTextures(0, Math.min(this.maxTextureImageUnits, this.mipmapLevels * 2), null);
        } else {
            for (int i = 0, limit = Math.min(this.maxTextureImageUnits, this.mipmapLevels * 2); i < limit; i++) {
                ARBShaderImageLoadStore.glBindImageTexture(i, 0, 0, false, 0, GL_READ_ONLY, GL_RGBA8);
            }
        }

        //ensure that texture writes are completed before rendering begins
        ARBShaderImageLoadStore.glMemoryBarrier(ARBShaderImageLoadStore.GL_TEXTURE_FETCH_BARRIER_BIT);
    }

    @Override
    public void close() {
        this.updateShaders.forEach(ShaderProgram::close);
        this.updateLists.values().forEach(UpdateItemList::close);
    }
}
