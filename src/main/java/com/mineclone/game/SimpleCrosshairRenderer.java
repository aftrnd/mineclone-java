package com.mineclone.game;

import org.joml.Matrix4f;
import org.lwjgl.BufferUtils;

import java.nio.FloatBuffer;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;

/**
 * Renders a Minecraft-style crosshair in the center of the screen.
 * White + with black outline for maximum visibility.
 */
public class SimpleCrosshairRenderer {
    private static final float SIZE = 0.015f;  // Size in NDC coordinates (small +)
    private static final float THICKNESS = 3.0f;
    
    private int vaoId;
    private int vboId;
    private int shaderProgram;
    private int projectionLoc;
    private int colorLoc;
    private boolean initialized = false;
    
    private void init(int screenWidth, int screenHeight) {
        // Create shader program
        shaderProgram = createShaderProgram();
        projectionLoc = glGetUniformLocation(shaderProgram, "projection");
        colorLoc = glGetUniformLocation(shaderProgram, "color");
        
        // Create VAO and VBO
        vaoId = glGenVertexArrays();
        vboId = glGenBuffers();
        
        // Crosshair + shape in NDC coordinates
        float[] vertices = {
            // Horizontal line
            -SIZE, 0.0f,
            SIZE, 0.0f,
            // Vertical line
            0.0f, -SIZE,
            0.0f, SIZE
        };
        
        FloatBuffer buffer = BufferUtils.createFloatBuffer(vertices.length);
        buffer.put(vertices).flip();
        
        glBindVertexArray(vaoId);
        glBindBuffer(GL_ARRAY_BUFFER, vboId);
        glBufferData(GL_ARRAY_BUFFER, buffer, GL_STATIC_DRAW);
        
        glVertexAttribPointer(0, 2, GL_FLOAT, false, 0, 0);
        glEnableVertexAttribArray(0);
        
        glBindVertexArray(0);
        initialized = true;
        
        System.out.println("✓ Crosshair initialized (Minecraft-style white + with black outline)");
    }
    
    private int createShaderProgram() {
        String vertexShaderSource = """
            #version 330 core
            layout (location = 0) in vec2 position;
            uniform mat4 projection;
            void main() {
                gl_Position = projection * vec4(position, 0.0, 1.0);
            }
            """;
        
        String fragmentShaderSource = """
            #version 330 core
            out vec4 FragColor;
            uniform vec4 color;
            void main() {
                FragColor = color;
            }
            """;
        
        int vertexShader = glCreateShader(GL_VERTEX_SHADER);
        glShaderSource(vertexShader, vertexShaderSource);
        glCompileShader(vertexShader);
        
        int fragmentShader = glCreateShader(GL_FRAGMENT_SHADER);
        glShaderSource(fragmentShader, fragmentShaderSource);
        glCompileShader(fragmentShader);
        
        int program = glCreateProgram();
        glAttachShader(program, vertexShader);
        glAttachShader(program, fragmentShader);
        glLinkProgram(program);
        
        glDeleteShader(vertexShader);
        glDeleteShader(fragmentShader);
        
        return program;
    }
    
    public void render(int screenWidth, int screenHeight) {
        if (!initialized) {
            init(screenWidth, screenHeight);
        }
        
        // Setup for 2D rendering
        boolean depthTestEnabled = glIsEnabled(GL_DEPTH_TEST);
        glDisable(GL_DEPTH_TEST);
        glDepthMask(false);
        
        // Use shader
        glUseProgram(shaderProgram);
        
        // Account for aspect ratio to make crosshair proportional
        float aspectRatio = (float) screenWidth / screenHeight;
        Matrix4f projection = new Matrix4f().identity();
        projection.scale(1.0f, aspectRatio, 1.0f);  // Scale Y by aspect ratio
        
        FloatBuffer projBuffer = BufferUtils.createFloatBuffer(16);
        projection.get(projBuffer);
        glUniformMatrix4fv(projectionLoc, false, projBuffer);
        
        glBindVertexArray(vaoId);
        
        // Draw black outline first (thicker)
        glLineWidth(THICKNESS + 2.0f);
        glUniform4f(colorLoc, 0.0f, 0.0f, 0.0f, 1.0f);
        glDrawArrays(GL_LINES, 0, 4);
        
        // Draw white crosshair on top
        glLineWidth(THICKNESS);
        glUniform4f(colorLoc, 1.0f, 1.0f, 1.0f, 1.0f);
        glDrawArrays(GL_LINES, 0, 4);
        
        glBindVertexArray(0);
        
        // Restore state
        glDepthMask(true);
        if (depthTestEnabled) {
            glEnable(GL_DEPTH_TEST);
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
