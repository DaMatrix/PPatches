package net.daporkchop.ppatches.modules.vanilla.optimizeTextureAnimationUpdates;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.ContextCapabilities;

import java.nio.IntBuffer;

import static com.google.common.base.Preconditions.checkArgument;
import static org.lwjgl.opengl.ARBBufferStorage.GL_DYNAMIC_STORAGE_BIT;
import static org.lwjgl.opengl.ARBDirectStateAccess.*;
import static org.lwjgl.opengl.ARBInvalidateSubdata.glInvalidateBufferData;
import static org.lwjgl.opengl.GL15.glDeleteBuffers;

/**
 * @author DaPorkchop_
 */
public final class UpdateItemList implements AutoCloseable {
    public static boolean isSupported(ContextCapabilities capabilities) {
        return (capabilities.OpenGL45 | capabilities.GL_ARB_direct_state_access)
                & (capabilities.OpenGL44 | capabilities.GL_ARB_buffer_storage)
                & (capabilities.OpenGL43 | capabilities.GL_ARB_invalidate_subdata);
    }

    private static final int SIZE_INTS = 8;

    private final IntBuffer cpuBuffer;
    public final int gpuBuffer;
    private int size = 0;

    public UpdateItemList(int size) {
        checkArgument(size >= 0, size);
        this.cpuBuffer = BufferUtils.createIntBuffer(Math.multiplyExact(size, SIZE_INTS));

        this.gpuBuffer = glCreateBuffers();
        glNamedBufferStorage(this.gpuBuffer, this.cpuBuffer.capacity() * (long) Integer.BYTES, GL_DYNAMIC_STORAGE_BIT);
    }

    public int size() {
        return this.size;
    }

    public void clear() {
        this.cpuBuffer.clear();
        this.size = 0;
    }

    public void add(int srcX, int srcY, int dstX, int dstY) {
        this.addInterpolate(srcX, srcY, srcX, srcY, dstX, dstY, 0.0f);
    }

    public void addInterpolate(int src0X, int src0Y, int src1X, int src1Y, int dstX, int dstY, float factor) {
        this.cpuBuffer
                .put(src0X).put(src0Y)
                .put(src1X).put(src1Y)
                .put(dstX).put(dstY)
                .put(Float.floatToRawIntBits(factor)).put(0);
        this.size++;
    }

    public void flush() {
        if (this.size == 0) {
            return;
        }

        glInvalidateBufferData(this.gpuBuffer);

        IntBuffer uploadRegion = this.cpuBuffer.duplicate();
        uploadRegion.flip();
        glNamedBufferSubData(this.gpuBuffer, 0L, uploadRegion);
    }

    @Override
    public void close() {
        glDeleteBuffers(this.gpuBuffer);
    }
}
