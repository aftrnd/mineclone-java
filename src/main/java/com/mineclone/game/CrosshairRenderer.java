package com.mineclone.game;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

import java.nio.FloatBuffer;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;

/**
 * Renders a simple crosshair in the center of the screen (Minecraft-style).
 * Uses a simple orthographic projection with white lines.
 */
public class CrosshairRenderer {
    private static final float CROSSHAIR_SIZE = 10.0f;  // Size in pixels
    private static final float CROSSHAIR_THICKNESS = 2.0f;
    private static final float CROSSHAIR_GAP = 2.0f;  // Gap in center
    
    private int vaoId;
    private int vboId;
    private boolean initialized = false;
    
    /**
     * Initialize the crosshair geometry.
     */
    private void init() {
        // Create VAO and VBO
        vaoId = glGenVertexArrays();
        vboId = glGenBuffers();
        
        // Crosshair vertices (two lines forming a +)
        // Normalized device coordinates (-1 to 1)
        float[] vertices = {
            // Horizontal line (left and right segments with gap)
            -CROSSHAIR_SIZE, 0.0f,
            -CROSSHAIR_GAP, 0.0f,
            
            CROSSHAIR_GAP, 0.0f,
            CROSSHAIR_SIZE, 0.0f,
            
            // Vertical line (top and bottom segments with gap)
            0.0f, -CROSSHAIR_SIZE,
            0.0f, -CROSSHAIR_GAP,
            
            0.0f, CROSSHAIR_GAP,
            0.0f, CROSSHAIR_SIZE
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
     * Render the crosshair at screen center.
     */
    public void render(int screenWidth, int screenHeight) {
        if (!initialized) {
            init();
        }
        
        // Save state
        glDisable(GL_DEPTH_TEST);
        boolean blendEnabled = glIsEnabled(GL_BLEND);
        if (!blendEnabled) {
            glEnable(GL_BLEND);
            glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        }
        
        // Use immediate mode for simplicity (crosshair is tiny, performance doesn't matter)
        glMatrixMode(GL_PROJECTION);
        glPushMatrix();
        glLoadIdentity();
        
        // Orthographic projection in pixel coordinates
        float halfWidth = screenWidth / 2.0f;
        float halfHeight = screenHeight / 2.0f;
        glOrtho(-halfWidth, halfWidth, halfHeight, -halfHeight, -1, 1);
        
        glMatrixMode(GL_MODELVIEW);
        glPushMatrix();
        glLoadIdentity();
        
        // Draw crosshair
        glColor4f(1.0f, 1.0f, 1.0f, 0.9f);  // White with slight transparency
        glLineWidth(CROSSHAIR_THICKNESS);
        
        glBegin(GL_LINES);
        // Horizontal line
        glVertex2f(-CROSSHAIR_SIZE, 0);
        glVertex2f(-CROSSHAIR_GAP, 0);
        glVertex2f(CROSSHAIR_GAP, 0);
        glVertex2f(CROSSHAIR_SIZE, 0);
        
        // Vertical line
        glVertex2f(0, -CROSSHAIR_SIZE);
        glVertex2f(0, -CROSSHAIR_GAP);
        glVertex2f(0, CROSSHAIR_GAP);
        glVertex2f(0, CROSSHAIR_SIZE);
        glEnd();
        
        // Restore matrices
        glPopMatrix();
        glMatrixMode(GL_PROJECTION);
        glPopMatrix();
        glMatrixMode(GL_MODELVIEW);
        
        // Restore state
        glEnable(GL_DEPTH_TEST);
        if (!blendEnabled) {
            glDisable(GL_BLEND);
        }
        glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
    }
    
    public void cleanup() {
        if (initialized) {
            glDeleteVertexArrays(vaoId);
            glDeleteBuffers(vboId);
        }
    }
}

