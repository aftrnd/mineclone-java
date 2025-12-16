package com.mineclone.game;

import org.joml.Matrix4f;
import org.lwjgl.BufferUtils;

import java.nio.FloatBuffer;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;

/**
 * Simple crosshair renderer using modern OpenGL (no deprecated functions).
 * Draws a + shape in the center of the screen.
 */
public class SimpleCrosshairRenderer {
    private static final float SIZE = 10.0f;  // Size in pixels
    private static final float THICKNESS = 2.0f;
    private static final float GAP = 3.0f;  // Gap in center
    
    private int vaoId;
    private int vboId;
    private int shaderProgram;
    private int projectionLoc;
    private int colorLoc;
    private boolean initialized = false;
    
    /**
     * Initialize the crosshair (called on first render).
     */
    private void init(int screenWidth, int screenHeight) {
        // Create shader program
        shaderProgram = createShaderProgram();
        projectionLoc = glGetUniformLocation(shaderProgram, "projection");
        colorLoc = glGetUniformLocation(shaderProgram, "color");
        
        // Create VAO and VBO
        vaoId = glGenVertexArrays();
        vboId = glGenBuffers();
        
        // Crosshair vertices (4 line segments forming a +)
        float[] vertices = {
            // Horizontal left
            -SIZE, 0.0f,
            -GAP, 0.0f,
            // Horizontal right
            GAP, 0.0f,
            SIZE, 0.0f,
            // Vertical top
            0.0f, -SIZE,
            0.0f, -GAP,
            // Vertical bottom
            0.0f, GAP,
            0.0f, SIZE
        };
        
        FloatBuffer buffer = BufferUtils.createFloatBuffer(vertices.length);
        buffer.put(vertices).flip();
        
        glBindVertexArray(vaoId);
        glBindBuffer(GL_ARRAY_BUFFER, vboId);
        glBufferData(GL_ARRAY_BUFFER, buffer, GL_STATIC_DRAW);
        
        // Position attribute
        glVertexAttribPointer(0, 2, GL_FLOAT, false, 0, 0);
        glEnableVertexAttribArray(0);
        
        glBindVertexArray(0);
        initialized = true;
    }
    
    /**
     * Create shader program for crosshair rendering.
     */
    private int createShaderProgram() {
        // Simple vertex shader (inline)
        String vertexShaderSource = """
            #version 330 core
            layout (location = 0) in vec2 position;
            uniform mat4 projection;
            void main() {
                gl_Position = projection * vec4(position, 0.0, 1.0);
            }
            """;
        
        // Simple fragment shader (inline)
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
     * Render the crosshair at screen center.
     */
    public void render(int screenWidth, int screenHeight) {
        if (!initialized) {
            init(screenWidth, screenHeight);
        }
        
        // Save OpenGL state
        boolean depthTestEnabled = glIsEnabled(GL_DEPTH_TEST);
        boolean blendEnabled = glIsEnabled(GL_BLEND);
        
        // Setup for 2D rendering
        glDisable(GL_DEPTH_TEST);
        if (!blendEnabled) {
            glEnable(GL_BLEND);
            glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        }
        
        // Use shader
        glUseProgram(shaderProgram);
        
        // Set orthographic projection (screen coordinates)
        Matrix4f projection = new Matrix4f().ortho(
            -screenWidth / 2.0f, screenWidth / 2.0f,
            screenHeight / 2.0f, -screenHeight / 2.0f,
            -1.0f, 1.0f
        );
        FloatBuffer projBuffer = BufferUtils.createFloatBuffer(16);
        projection.get(projBuffer);
        glUniformMatrix4fv(projectionLoc, false, projBuffer);
        
        // Set color (white with slight transparency)
        glUniform4f(colorLoc, 1.0f, 1.0f, 1.0f, 0.9f);
        
        // Draw crosshair
        glBindVertexArray(vaoId);
        glLineWidth(THICKNESS);
        glDrawArrays(GL_LINES, 0, 8);
        glBindVertexArray(0);
        
        // Restore OpenGL state
        if (depthTestEnabled) {
            glEnable(GL_DEPTH_TEST);
        }
        if (!blendEnabled) {
            glDisable(GL_BLEND);
        }
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

