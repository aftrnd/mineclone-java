package com.mineclone.game;

import org.joml.Matrix4f;
import org.joml.Vector3i;
import org.lwjgl.BufferUtils;

import java.nio.FloatBuffer;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;

/**
 * Simple block outline renderer using modern OpenGL.
 * Draws a wireframe cube around the targeted block.
 */
public class SimpleBlockOutlineRenderer {
    private static final float EXPAND = 0.002f;  // Slightly larger than block to prevent z-fighting
    private static final float LINE_WIDTH = 2.0f;
    
    private int vaoId;
    private int vboId;
    private int shaderProgram;
    private int projectionLoc;
    private int viewLoc;
    private int modelLoc;
    private int colorLoc;
    private boolean initialized = false;
    
    /**
     * Initialize the outline renderer.
     */
    private void init() {
        // Create shader program
        shaderProgram = createShaderProgram();
        projectionLoc = glGetUniformLocation(shaderProgram, "projection");
        viewLoc = glGetUniformLocation(shaderProgram, "view");
        modelLoc = glGetUniformLocation(shaderProgram, "model");
        colorLoc = glGetUniformLocation(shaderProgram, "color");
        
        // Create VAO and VBO
        vaoId = glGenVertexArrays();
        vboId = glGenBuffers();
        
        // Wireframe cube vertices (unit cube at origin)
        float[] vertices = {
            // Bottom face
            0, 0, 0,  1, 0, 0,
            1, 0, 0,  1, 0, 1,
            1, 0, 1,  0, 0, 1,
            0, 0, 1,  0, 0, 0,
            // Top face
            0, 1, 0,  1, 1, 0,
            1, 1, 0,  1, 1, 1,
            1, 1, 1,  0, 1, 1,
            0, 1, 1,  0, 1, 0,
            // Vertical edges
            0, 0, 0,  0, 1, 0,
            1, 0, 0,  1, 1, 0,
            1, 0, 1,  1, 1, 1,
            0, 0, 1,  0, 1, 1
        };
        
        FloatBuffer buffer = BufferUtils.createFloatBuffer(vertices.length);
        buffer.put(vertices).flip();
        
        glBindVertexArray(vaoId);
        glBindBuffer(GL_ARRAY_BUFFER, vboId);
        glBufferData(GL_ARRAY_BUFFER, buffer, GL_STATIC_DRAW);
        
        // Position attribute
        glVertexAttribPointer(0, 3, GL_FLOAT, false, 0, 0);
        glEnableVertexAttribArray(0);
        
        glBindVertexArray(0);
        initialized = true;
    }
    
    /**
     * Create shader program for outline rendering.
     */
    private int createShaderProgram() {
        // Simple vertex shader
        String vertexShaderSource = """
            #version 330 core
            layout (location = 0) in vec3 position;
            uniform mat4 projection;
            uniform mat4 view;
            uniform mat4 model;
            void main() {
                gl_Position = projection * view * model * vec4(position, 1.0);
            }
            """;
        
        // Simple fragment shader
        String fragmentShaderSource = """
            #version 330 core
            out vec4 FragColor;
            uniform vec4 color;
            void main() {
                FragColor = color;
            }
            """;
        
        // Compile shaders
        int vertexShader = glCreateShader(GL_VERTEX_SHADER);
        glShaderSource(vertexShader, vertexShaderSource);
        glCompileShader(vertexShader);
        
        int fragmentShader = glCreateShader(GL_FRAGMENT_SHADER);
        glShaderSource(fragmentShader, fragmentShaderSource);
        glCompileShader(fragmentShader);
        
        // Link program
        int program = glCreateProgram();
        glAttachShader(program, vertexShader);
        glAttachShader(program, fragmentShader);
        glLinkProgram(program);
        
        // Cleanup
        glDeleteShader(vertexShader);
        glDeleteShader(fragmentShader);
        
        return program;
    }
    
    /**
     * Render outline around a block.
     */
    public void render(Vector3i blockPos, Matrix4f viewMatrix, Matrix4f projectionMatrix) {
        if (!initialized) {
            init();
        }
        
        // Save OpenGL state
        boolean depthTestEnabled = glIsEnabled(GL_DEPTH_TEST);
        int depthFunc = glGetInteger(GL_DEPTH_FUNC);
        
        // Disable depth test to draw on top
        glDisable(GL_DEPTH_TEST);
        
        // Use shader
        glUseProgram(shaderProgram);
        
        // Set matrices
        FloatBuffer projBuffer = BufferUtils.createFloatBuffer(16);
        projectionMatrix.get(projBuffer);
        glUniformMatrix4fv(projectionLoc, false, projBuffer);
        
        FloatBuffer viewBuffer = BufferUtils.createFloatBuffer(16);
        viewMatrix.get(viewBuffer);
        glUniformMatrix4fv(viewLoc, false, viewBuffer);
        
        // Model matrix (translate to block position and scale slightly)
        Matrix4f modelMatrix = new Matrix4f()
            .translate(blockPos.x - EXPAND, blockPos.y - EXPAND, blockPos.z - EXPAND)
            .scale(1.0f + EXPAND * 2);
        FloatBuffer modelBuffer = BufferUtils.createFloatBuffer(16);
        modelMatrix.get(modelBuffer);
        glUniformMatrix4fv(modelLoc, false, modelBuffer);
        
        // Set color (black outline)
        glUniform4f(colorLoc, 0.0f, 0.0f, 0.0f, 0.8f);
        
        // Draw outline
        glBindVertexArray(vaoId);
        glLineWidth(LINE_WIDTH);
        glDrawArrays(GL_LINES, 0, 24);
        glBindVertexArray(0);
        
        // Restore OpenGL state
        if (depthTestEnabled) {
            glEnable(GL_DEPTH_TEST);
        }
        glDepthFunc(depthFunc);
        glUseProgram(0);
    }
    
    public void cleanup() {
        if (initialized) {
            glDeleteVertexArrays(vaoId);
            glDeleteBuffers(vboId);
            glDeleteProgram(shaderProgram);
        }
    }
}


