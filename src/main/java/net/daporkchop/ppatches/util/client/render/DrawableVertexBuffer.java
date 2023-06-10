package net.daporkchop.ppatches.util.client.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.vertex.VertexFormat;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.opengl.GL30;

import java.nio.ByteBuffer;

/**
 * @author DaPorkchop_
 */
@SideOnly(Side.CLIENT)
public class DrawableVertexBuffer {
    public static DrawableVertexBuffer fromUnfinishedBuffer(BufferBuilder builder) {
        int vertexCount = builder.getVertexCount();
        VertexFormat format = builder.getVertexFormat();
        return new DrawableVertexBuffer(builder.getDrawMode(), vertexCount, format,
                (ByteBuffer) builder.getByteBuffer().duplicate().position(0).limit(vertexCount * format.getSize()));
    }

    public static DrawableVertexBuffer fromFinishedBuffer(BufferBuilder builder) {
        return fromUnfinishedBuffer(builder);
    }

    public static DrawableVertexBuffer fromResetBuffer(BufferBuilder builder) {
        VertexFormat format = builder.getVertexFormat();
        ByteBuffer data = builder.getByteBuffer();
        int vertexCount = data.limit() / format.getSize();
        return new DrawableVertexBuffer(builder.getDrawMode(), vertexCount, format, (ByteBuffer) data.duplicate().rewind());
    }

    protected final int vao;
    protected final int buffer;

    protected final int mode;
    protected int count;

    protected DrawableVertexBuffer(int mode, int count, VertexFormat format, ByteBuffer data) {
        assert data.remaining() == count * format.getSize();

        this.mode = mode;
        this.count = count;

        if (this.count > 0) {
            this.buffer = OpenGlHelper.glGenBuffers();
            OpenGlHelper.glBindBuffer(OpenGlHelper.GL_ARRAY_BUFFER, this.buffer);
            OpenGlHelper.glBufferData(OpenGlHelper.GL_ARRAY_BUFFER, data, OpenGlHelper.GL_STATIC_DRAW);
            OpenGlHelper.glBindBuffer(OpenGlHelper.GL_ARRAY_BUFFER, 0);

            this.vao = GL30.glGenVertexArrays();
            GL30.glBindVertexArray(this.vao);
            OpenGlHelper.glBindBuffer(OpenGlHelper.GL_ARRAY_BUFFER, this.buffer);
            RenderUtils.prepareVertexAttributesFromVBO(format);
            OpenGlHelper.glBindBuffer(OpenGlHelper.GL_ARRAY_BUFFER, 0);
            GL30.glBindVertexArray(0);
        } else {
            this.vao = 0;
            this.buffer = 0;
        }
    }

    public void dispose() {
        if (this.count > 0) {
            this.count = 0;
            GL30.glDeleteVertexArrays(this.vao);
            OpenGlHelper.glDeleteBuffers(this.buffer);
        }
    }

    @Override
    protected void finalize() { //i don't care that this is gross
        if (this.count > 0) {
            Minecraft.getMinecraft().addScheduledTask(this::dispose);
        }
    }

    public void draw() {
        if (this.count > 0) {
            GL30.glBindVertexArray(this.vao);
            GlStateManager.glDrawArrays(this.mode, 0, this.count);
            GL30.glBindVertexArray(0);
        }
    }
}
