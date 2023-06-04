package net.daporkchop.ppatches.modules.vanilla.optimizetessellatordraw;

import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.WorldVertexBufferUploader;
import net.minecraft.client.renderer.vertex.VertexFormat;
import net.minecraft.client.renderer.vertex.VertexFormatElement;
import net.minecraftforge.fml.common.FMLLog;
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
import java.util.List;

/**
 * @author DaPorkchop_
 */
public class VAOWorldVertexBufferUploader extends WorldVertexBufferUploader {
    private static final int BUFFER_CAPACITY = 16 << 20;

    private static final int FLAGS = GL30.GL_MAP_WRITE_BIT | GL44.GL_MAP_PERSISTENT_BIT | GL44.GL_MAP_COHERENT_BIT;
    private static final int MAP_FLAGS = FLAGS | GL30.GL_MAP_INVALIDATE_BUFFER_BIT;
    private static final int STORAGE_FLAGS = FLAGS | GL44.GL_CLIENT_STORAGE_BIT;

    private final Reference2IntOpenHashMap<VertexFormat> format2vao = new Reference2IntOpenHashMap<>();

    private final boolean directStateAccess;

    private int buffer;
    private ByteBuffer bufferData;

    public VAOWorldVertexBufferUploader() {
        ContextCapabilities capabilities = GLContext.getCapabilities();

        if (!(capabilities.OpenGL44 | capabilities.GL_ARB_buffer_storage)) { //we need persistently mapped buffers
            throw new UnsupportedOperationException("OpenGL 4.4 or ARB_buffer_storage is required!");
        }

        this.directStateAccess = capabilities.OpenGL45 | capabilities.GL_ARB_direct_state_access | capabilities.GL_EXT_direct_state_access;

        this.recreateBuffer();
    }

    private void recreateBuffer() {
        if (this.buffer != 0) {
            ARBDirectStateAccess.glUnmapNamedBuffer(this.buffer);
            this.bufferData = null;

            OpenGlHelper.glDeleteBuffers(this.buffer);

            //vertex formats are now invalid too
            for (int vao : this.format2vao.values()) {
                GL30.glDeleteVertexArrays(vao);
            }
            this.format2vao.clear();
        }

        if (this.directStateAccess) {
            this.buffer = ARBDirectStateAccess.glCreateBuffers();
            ARBDirectStateAccess.glNamedBufferStorage(this.buffer, BUFFER_CAPACITY, STORAGE_FLAGS);
            this.bufferData = ARBDirectStateAccess.glMapNamedBufferRange(this.buffer, 0L, BUFFER_CAPACITY, MAP_FLAGS, this.bufferData);
        } else {
            this.buffer = OpenGlHelper.glGenBuffers();
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, this.buffer);
            ARBBufferStorage.glBufferStorage(GL15.GL_ARRAY_BUFFER, BUFFER_CAPACITY, STORAGE_FLAGS);
            this.bufferData = GL30.glMapBufferRange(GL15.GL_ARRAY_BUFFER, 0L, BUFFER_CAPACITY, MAP_FLAGS, this.bufferData);
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        }

        this.bufferData.clear();
    }

    private void rewindBuffer() {
        //unmap the buffer, then re-map it with GL_MAP_INVALIDATE_BUFFER_BIT to allow orphaning
        //  (this prevents us from having to deal with a bunch of extra synchronization stuff)
        if ((MAP_FLAGS & GL30.GL_MAP_INVALIDATE_BUFFER_BIT) != 0) {
            if (this.directStateAccess) {
                ARBDirectStateAccess.glUnmapNamedBuffer(this.buffer);
                this.bufferData = ARBDirectStateAccess.glMapNamedBufferRange(this.buffer, 0L, BUFFER_CAPACITY, MAP_FLAGS, this.bufferData);
            } else {
                GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, this.buffer);
                GL15.glUnmapBuffer(GL15.GL_ARRAY_BUFFER);
                this.bufferData = GL30.glMapBufferRange(GL15.GL_ARRAY_BUFFER, 0L, BUFFER_CAPACITY, MAP_FLAGS, this.bufferData);
                GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
            }
        }

        this.bufferData.clear();
    }

    @Override
    protected void finalize() { //i don't care that this is gross
        Minecraft.getMinecraft().addScheduledTask(() -> {
            for (int vao : this.format2vao.values()) {
                GL30.glDeleteVertexArrays(vao);
            }
            this.format2vao.clear();

            if (this.buffer != 0) {
                ARBDirectStateAccess.glUnmapNamedBuffer(this.buffer);
                OpenGlHelper.glDeleteBuffers(this.buffer);
            }
        });
    }

    @Override
    public void draw(BufferBuilder builder) {
        if (builder.getVertexCount() == 0) {
            return;
        }

        if (builder.getByteBuffer().remaining() > BUFFER_CAPACITY) { //buffer is too large!
            super.draw(builder);
            return;
        }

        VertexFormat currFormat = builder.getVertexFormat();

        int vertexSize = currFormat.getSize();
        if (this.bufferData.position() % vertexSize != 0) { //align to vertexSize
            int alignedPosition = this.bufferData.position() - this.bufferData.position() % vertexSize + vertexSize;
            if (alignedPosition >= this.bufferData.limit()) {
                this.rewindBuffer();
            } else {
                this.bufferData.position(alignedPosition);
            }
        }
        if (this.bufferData.remaining() < builder.getByteBuffer().remaining()) { //reset if buffer is full
            this.rewindBuffer();
        }

        int vao = this.format2vao.getInt(currFormat);
        if (vao == 0) {
            vao = this.configureVAO(currFormat);
        }

        //upload vertex data
        int firstVertex = this.bufferData.position() / vertexSize;
        this.bufferData.put(builder.getByteBuffer().slice());

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

        int stride = currFormat.getSize();
        List<VertexFormatElement> list = currFormat.getElements();
        for (int j = 0; j < list.size(); ++j) {
            VertexFormatElement attr = list.get(j);
            int offset = currFormat.getOffset(j);

            // moved to VertexFormatElement.preDraw
            int count = attr.getElementCount();
            int constant = attr.getType().getGlConstant();
            switch (attr.getUsage()) {
                case POSITION:
                    GlStateManager.glVertexPointer(count, constant, stride, offset);
                    GlStateManager.glEnableClientState(GL11.GL_VERTEX_ARRAY);
                    break;
                case NORMAL:
                    if (count != 3) {
                        throw new IllegalArgumentException("Normal attribute should have the size 3: " + attr);
                    }
                    GL11.glNormalPointer(constant, stride, offset);
                    GlStateManager.glEnableClientState(GL11.GL_NORMAL_ARRAY);
                    break;
                case COLOR:
                    GlStateManager.glColorPointer(count, constant, stride, offset);
                    GlStateManager.glEnableClientState(GL11.GL_COLOR_ARRAY);
                    break;
                case UV:
                    OpenGlHelper.setClientActiveTexture(OpenGlHelper.defaultTexUnit + attr.getIndex());
                    GlStateManager.glTexCoordPointer(count, constant, stride, offset);
                    GlStateManager.glEnableClientState(GL11.GL_TEXTURE_COORD_ARRAY);
                    OpenGlHelper.setClientActiveTexture(OpenGlHelper.defaultTexUnit);
                    break;
                case PADDING:
                    break;
                case GENERIC:
                    GL20.glEnableVertexAttribArray(attr.getIndex());
                    GL20.glVertexAttribPointer(attr.getIndex(), count, constant, false, stride, offset);
                    break;
                default:
                    FMLLog.log.fatal("Unimplemented vanilla attribute upload: {}", attr.getUsage().getDisplayName());
            }
        }

        OpenGlHelper.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        GL30.glBindVertexArray(0);

        //cache configured vao
        this.format2vao.put(currFormat, vao);

        return vao;
    }
}
