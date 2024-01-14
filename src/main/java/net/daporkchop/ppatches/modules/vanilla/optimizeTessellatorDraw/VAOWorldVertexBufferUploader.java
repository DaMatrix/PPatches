package net.daporkchop.ppatches.modules.vanilla.optimizeTessellatorDraw;

import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap;
import net.daporkchop.ppatches.PPatchesConfig;
import net.daporkchop.ppatches.PPatchesMod;
import net.daporkchop.ppatches.util.client.render.RenderUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.WorldVertexBufferUploader;
import net.minecraft.client.renderer.vertex.VertexFormat;
import net.minecraft.client.renderer.vertex.VertexFormatElement;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.fml.common.FMLLog;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.opengl.ARBBufferStorage;
import org.lwjgl.opengl.ARBDirectStateAccess;
import org.lwjgl.opengl.ContextCapabilities;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL44;
import org.lwjgl.opengl.GLContext;

import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * @author DaPorkchop_
 */
@SideOnly(Side.CLIENT)
public class VAOWorldVertexBufferUploader extends WorldVertexBufferUploader {
    public static final Set<VAOWorldVertexBufferUploader> ALL_INSTANCES = Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));

    protected static final int STAGING_BUFFER_FLAGS = GL30.GL_MAP_WRITE_BIT | GL44.GL_MAP_PERSISTENT_BIT | GL44.GL_MAP_COHERENT_BIT;

    protected final Reference2IntOpenHashMap<VertexFormat> format2vao = new Reference2IntOpenHashMap<>();

    protected final boolean directStateAccess;

    protected int buffer;
    protected ByteBuffer stagingBuffer;

    public VAOWorldVertexBufferUploader() {
        ContextCapabilities capabilities = GLContext.getCapabilities();
        this.directStateAccess = capabilities.OpenGL45 | capabilities.GL_ARB_direct_state_access;

        this.recreateBuffer();

        //register self to listen for config reloads
        ALL_INSTANCES.add(this);
    }

    @Override
    protected void finalize() { //i don't care that this is gross
        Minecraft.getMinecraft().addScheduledTask(this::cleanup);
    }

    protected void cleanup() {
        if (!this.format2vao.isEmpty()) {
            for (int vao : this.format2vao.values()) {
                GL30.glDeleteVertexArrays(vao);
            }
            this.format2vao.clear();
        }

        if (this.buffer != 0) {
            if (this.stagingBuffer != null) { //unmap the staging buffer before deleting it
                if (this.directStateAccess) {
                    ARBDirectStateAccess.glUnmapNamedBuffer(this.buffer);
                } else {
                    GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, this.buffer);
                    GL15.glUnmapBuffer(GL15.GL_ARRAY_BUFFER);
                    GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
                }
                this.stagingBuffer = null;
            }
            OpenGlHelper.glDeleteBuffers(this.buffer);
            this.buffer = 0;
        }
    }

    public void onConfigReload() {
        this.recreateBuffer();
    }

    protected void recreateBuffer() {
        this.cleanup();

        //create new opengl buffer
        this.buffer = this.directStateAccess ? ARBDirectStateAccess.glCreateBuffers() : OpenGlHelper.glGenBuffers();

        switch (PPatchesConfig.vanilla_optimizeTessellatorDraw.mode) {
            case STAGING_BUFFER: {
                ContextCapabilities capabilities = GLContext.getCapabilities();
                if (capabilities.OpenGL44 | capabilities.GL_ARB_buffer_storage) { //we have persistently mapped buffers
                    int stagingBufferCapacity = Math.multiplyExact(PPatchesConfig.vanilla_optimizeTessellatorDraw.stagingBufferCapacity, 1024);
                    int storageFlags = STAGING_BUFFER_FLAGS | (PPatchesConfig.vanilla_optimizeTessellatorDraw.stagingBufferClientStorage ? GL44.GL_CLIENT_STORAGE_BIT : 0);

                    //allocate buffer storage and create a persistent mapping
                    if (this.directStateAccess) {
                        ARBDirectStateAccess.glNamedBufferStorage(this.buffer, stagingBufferCapacity, storageFlags);
                        this.stagingBuffer = ARBDirectStateAccess.glMapNamedBufferRange(this.buffer, 0L, stagingBufferCapacity, STAGING_BUFFER_FLAGS, this.stagingBuffer);
                    } else {
                        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, this.buffer);
                        ARBBufferStorage.glBufferStorage(GL15.GL_ARRAY_BUFFER, stagingBufferCapacity, storageFlags);
                        this.stagingBuffer = GL30.glMapBufferRange(GL15.GL_ARRAY_BUFFER, 0L, stagingBufferCapacity, STAGING_BUFFER_FLAGS, this.stagingBuffer);
                        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
                    }
                    this.stagingBuffer.clear();
                    break;
                }

                String msg = "vanilla.optimizeTessellatorDraw: OpenGL 4.4 or ARB_buffer_storage is required to use STAGING_BUFFER mode! Falling back to ORPHAN_BUFFER.";
                if (Minecraft.getMinecraft().player != null) {
                    Minecraft.getMinecraft().player.sendMessage(new TextComponentString(TextFormatting.RED + msg));
                } else {
                    PPatchesMod.LOGGER.error(msg);
                }
                //fall through
            }
            case ORPHAN_BUFFER:
                //set the buffer to null so we know we're not using a staging buffer
                this.stagingBuffer = null;
                break;
            default:
                throw new IllegalStateException(PPatchesConfig.vanilla_optimizeTessellatorDraw.mode.name());
        }
    }

    protected void rewindStagingBuffer() {
        if (PPatchesConfig.vanilla_optimizeTessellatorDraw.stagingBufferLogOnReset) {
            PPatchesMod.LOGGER.info("Staging buffer {} is full on frame #{}, resetting...", this.buffer, Minecraft.getMinecraft().getFrameTimer().getIndex());
        }

        if (PPatchesConfig.vanilla_optimizeTessellatorDraw.stagingBufferInvalidateOnReset) {
            //unmap the buffer, then re-map it with GL_MAP_INVALIDATE_BUFFER_BIT to allow orphaning
            //  (this prevents us from having to deal with a bunch of extra synchronization stuff)
            if (this.directStateAccess) {
                ARBDirectStateAccess.glUnmapNamedBuffer(this.buffer);
                this.stagingBuffer = ARBDirectStateAccess.glMapNamedBufferRange(this.buffer, 0L, this.stagingBuffer.capacity(), STAGING_BUFFER_FLAGS | GL30.GL_MAP_INVALIDATE_BUFFER_BIT, this.stagingBuffer);
            } else {
                GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, this.buffer);
                GL15.glUnmapBuffer(GL15.GL_ARRAY_BUFFER);
                this.stagingBuffer = GL30.glMapBufferRange(GL15.GL_ARRAY_BUFFER, 0L, this.stagingBuffer.capacity(), STAGING_BUFFER_FLAGS | GL30.GL_MAP_INVALIDATE_BUFFER_BIT, this.stagingBuffer);
                GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
            }
        } else {
            //TODO: we probably need to use a sync object to make sure we aren't overwriting data which is still being rendered, for now we'll just assume the staging
            //  buffer is big enough that it won't be an issue
        }

        this.stagingBuffer.clear();
    }

    @Override
    public void draw(BufferBuilder builder) {
        if (builder.getVertexCount() == 0) { //nothing to draw!
            builder.reset();
            return;
        }

        VertexFormat currFormat = builder.getVertexFormat();
        ByteBuffer drawBuffer = builder.getByteBuffer();

        int firstVertex;
        if (this.stagingBuffer != null) { //mode == STAGING_BUFFER
            if (drawBuffer.remaining() > this.stagingBuffer.capacity()) { //buffer is bigger than the staging buffer!
                super.draw(builder);
                return;
            }

            int vertexSize = currFormat.getSize();
            if (this.stagingBuffer.position() % vertexSize != 0) { //align to vertexSize
                int alignedPosition = this.stagingBuffer.position() - this.stagingBuffer.position() % vertexSize + vertexSize;
                if (alignedPosition >= this.stagingBuffer.limit()) {
                    this.rewindStagingBuffer();
                } else {
                    this.stagingBuffer.position(alignedPosition);
                }
            }
            if (this.stagingBuffer.remaining() < drawBuffer.remaining()) { //reset if buffer is full
                this.rewindStagingBuffer();
            }

            //upload vertex data
            firstVertex = this.stagingBuffer.position() / vertexSize;
            this.stagingBuffer.put(drawBuffer.slice());
        } else { //mode == ORPHAN_BUFFER
            firstVertex = 0;

            //orphan old buffer contents and upload new vertex data
            if (this.directStateAccess) {
                ARBDirectStateAccess.glNamedBufferData(this.buffer, drawBuffer, GL15.GL_STREAM_DRAW);
            } else {
                OpenGlHelper.glBindBuffer(GL15.GL_ARRAY_BUFFER, this.buffer);
                OpenGlHelper.glBufferData(GL15.GL_ARRAY_BUFFER, drawBuffer, GL15.GL_STREAM_DRAW);
                OpenGlHelper.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
            }
        }

        int vao = this.format2vao.getInt(currFormat);
        if (vao == 0) { //we haven't cached a VAO for this vertex format
            vao = this.configureVAO(currFormat);
        }

        //actually draw stuff
        GL30.glBindVertexArray(vao);
        GlStateManager.glDrawArrays(builder.getDrawMode(), firstVertex, builder.getVertexCount());
        GL30.glBindVertexArray(0);

        builder.reset();
    }

    private int configureVAO(VertexFormat currFormat) {
        //create and configure new VAO
        int vao = GL30.glGenVertexArrays();
        if (vao == 0) {
            throw new IllegalStateException();
        }

        GL30.glBindVertexArray(vao);
        OpenGlHelper.glBindBuffer(GL15.GL_ARRAY_BUFFER, this.buffer);

        RenderUtils.prepareVertexAttributesFromVBO(currFormat);

        OpenGlHelper.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        GL30.glBindVertexArray(0);

        //cache configured vao
        this.format2vao.put(currFormat, vao);

        return vao;
    }
}
