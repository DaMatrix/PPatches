package net.daporkchop.ppatches.util.client.render;

import java.util.List;

import static org.lwjgl.opengl.GL11.GL_FALSE;
import static org.lwjgl.opengl.GL20.*;

/**
 * @author DaPorkchop_
 */
public final class Shader implements AutoCloseable {
    public final int id;

    public Shader(int type, List<String> lines) {
        this.id = glCreateShader(type);

        try {
            //set source and compile shader
            glShaderSource(this.id, String.join("\n", lines));
            glCompileShader(this.id);

            //check for errors
            if (glGetShaderi(this.id, GL_COMPILE_STATUS) == GL_FALSE) {
                throw new RuntimeException(glGetShaderInfoLog(this.id, glGetShaderi(this.id, GL_INFO_LOG_LENGTH)));
            }
        } catch (Throwable t) { //clean up if something goes wrong
            glDeleteShader(this.id);
            throw t;
        }
    }

    @Override
    public void close() {
        glDeleteShader(this.id);
    }
}
