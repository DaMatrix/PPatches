package net.daporkchop.ppatches.modules.vanilla.optimizeVertexBufferCreation;

import lombok.experimental.UtilityClass;
import net.daporkchop.ppatches.PPatchesMod;
import net.minecraft.client.renderer.GLAllocation;
import org.lwjgl.opengl.GL15;

import java.nio.IntBuffer;

/**
 * @author DaPorkchop_
 */
@UtilityClass
public class OptimizeVertexBufferCreation {
    public static IntBuffer _PREALLOCATED_BUFFER_IDS = null;

    public static void preAllocateBuffers(int count) {
        if (count <= 0) { //nothing to do
            return;
        }

        if (_PREALLOCATED_BUFFER_IDS != null && count > _PREALLOCATED_BUFFER_IDS.remaining()) { //if the number of leftover buffers isn't sufficient, delete them
            GL15.glDeleteBuffers(_PREALLOCATED_BUFFER_IDS.slice());
        }

        _PREALLOCATED_BUFFER_IDS = GLAllocation.createDirectIntBuffer(count);
        GL15.glGenBuffers(_PREALLOCATED_BUFFER_IDS);
    }
}
