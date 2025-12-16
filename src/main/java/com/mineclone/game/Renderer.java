package com.mineclone.game;

import com.mineclone.core.ResourceLoader;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;

/**
 * Modern OpenGL renderer for Minecraft clone using shaders
 */
public class Renderer {
    private ShaderProgram shaderProgram;
    private int vaoId;
    private int vboId;
    private int vertexCount;

    public Renderer() throws Exception {
        // Create shader program
        shaderProgram = new ShaderProgram();
        shaderProgram.createVertexShader(ResourceLoader.loadResource("/shaders/vertex.glsl"));
        shaderProgram.createFragmentShader(ResourceLoader.loadResource("/shaders/fragment.glsl"));
        shaderProgram.link();

        // Create uniforms
        shaderProgram.createUniform("projection");
        shaderProgram.createUniform("view");
        shaderProgram.createUniform("model");

        // Create VAO and VBO
        vaoId = glGenVertexArrays();
        vboId = glGenBuffers();
        System.out.println("Created VAO: " + vaoId + ", VBO: " + vboId);
    }

    public void render(Camera camera, Chunk chunk, Matrix4f projectionMatrix) {
        // Enable depth testing and face culling
        glEnable(GL_DEPTH_TEST);
        glDepthFunc(GL_LESS);
        glEnable(GL_CULL_FACE);
        glCullFace(GL_BACK);

        shaderProgram.bind();

        // Set projection matrix
        shaderProgram.setUniform("projection", projectionMatrix);

        // Set view matrix (from camera)
        Matrix4f viewMatrix = camera.getViewMatrix();
        shaderProgram.setUniform("view", viewMatrix);

        // Set model matrix - translate to chunk's world position
        ChunkPos pos = chunk.getPos();
        Matrix4f modelMatrix = new Matrix4f().identity()
            .translate(pos.getWorldX(), 0, pos.getWorldZ());
        shaderProgram.setUniform("model", modelMatrix);

        // Generate mesh data (position + color per vertex = 6 floats)
        float[] vertices = chunk.generateMesh();
        vertexCount = vertices.length / 6;  // 6 floats per vertex (xyz + rgb)

        if (vertexCount > 0) {
            // Upload vertex data
            glBindVertexArray(vaoId);
            glBindBuffer(GL_ARRAY_BUFFER, vboId);

            // Create buffer for vertices
            FloatBuffer vertexBuffer = org.lwjgl.BufferUtils.createFloatBuffer(vertices.length);
            vertexBuffer.put(vertices).flip();
            glBufferData(GL_ARRAY_BUFFER, vertexBuffer, GL_STATIC_DRAW);

            int stride = 6 * Float.BYTES;  // 6 floats per vertex
            
            // Position attribute (location = 0): 3 floats at offset 0
            glVertexAttribPointer(0, 3, GL_FLOAT, false, stride, 0);
            glEnableVertexAttribArray(0);
            
            // Color attribute (location = 1): 3 floats at offset 3*4 bytes
            glVertexAttribPointer(1, 3, GL_FLOAT, false, stride, 3 * Float.BYTES);
            glEnableVertexAttribArray(1);

            // Draw triangles
            glDrawArrays(GL_TRIANGLES, 0, vertexCount);

            // Cleanup
            glDisableVertexAttribArray(0);
            glDisableVertexAttribArray(1);
            glBindBuffer(GL_ARRAY_BUFFER, 0);
            glBindVertexArray(0);
        }

        shaderProgram.unbind();
    }

    public void cleanup() {
        shaderProgram.cleanup();
        if (vboId != 0) {
            glDeleteBuffers(vboId);
        }
        if (vaoId != 0) {
            glDeleteVertexArrays(vaoId);
        }
    }
}