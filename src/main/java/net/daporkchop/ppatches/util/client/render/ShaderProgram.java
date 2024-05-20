package net.daporkchop.ppatches.util.client.render;

import java.util.List;

import static org.lwjgl.opengl.GL11.GL_FALSE;
import static org.lwjgl.opengl.GL11.glGetInteger;
import static org.lwjgl.opengl.GL20.*;

/**
 * @author DaPorkchop_
 */
public final class ShaderProgram implements AutoCloseable {
    public final int id;

    public ShaderProgram(List<Shader> shaders) {
        this.id = glCreateProgram();

        try {
            //attach shaders and link, then detach shaders again
            for (Shader shader : shaders) {
                glAttachShader(this.id, shader.id);
            }
            glLinkProgram(this.id);
            for (Shader shader : shaders) {
                glDetachShader(this.id, shader.id);
            }

            //check for errors
            if (glGetProgrami(this.id, GL_LINK_STATUS) == GL_FALSE) {
                throw new RuntimeException(glGetProgramInfoLog(this.id, glGetProgrami(this.id, GL_INFO_LOG_LENGTH)));
            }
        } catch (Throwable t) { //clean up if something goes wrong
            glDeleteProgram(this.id);
            throw t;
        }
    }

    @Override
    public void close() {
        glDeleteProgram(this.id);
    }

    /**
     * Executes the given action with this program bound as the active program.
     *
     * @param action the action to run while the shader is bound
     */
    public void bind(Runnable action) {
        int old = glGetInteger(GL_CURRENT_PROGRAM);
        try {
            glUseProgram(this.id);
            action.run();
        } finally {
            glUseProgram(old);
        }
    }

    /**
     * Gets the location of the uniform with the given name.
     *
     * @param name the uniform name
     * @return the uniform's location
     */
    public int uniformLocation(String name) {
        return glGetUniformLocation(this.id, name);
    }
}
