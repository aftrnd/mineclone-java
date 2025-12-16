package com.mineclone.game;

import org.joml.Matrix4f;
import org.joml.Vector3i;

import static org.lwjgl.opengl.GL11.*;

/**
 * Renders an outline around the block the player is looking at (Minecraft-style).
 * Draws a wireframe cube slightly larger than the block for visual feedback.
 */
public class BlockOutlineRenderer {
    private static final float OUTLINE_OFFSET = 0.002f;  // Slightly larger than block to prevent z-fighting
    private static final float LINE_WIDTH = 2.0f;
    
    /**
     * Render an outline around a block.
     * @param blockPos Position of the block to outline
     * @param viewMatrix Camera view matrix
     * @param projectionMatrix Projection matrix
     */
    public void render(Vector3i blockPos, Matrix4f viewMatrix, Matrix4f projectionMatrix) {
        // Temporarily disabled - will implement with proper shader support
        // For now, we can still see which block we're targeting via console output
        // TODO: Implement proper wireframe rendering with shaders (no deprecated glMatrixMode)
    }
    
    /**
     * Draw a wireframe cube.
     */
    private void drawWireCube(float x, float y, float z, float size) {
        float x1 = x + size;
        float y1 = y + size;
        float z1 = z + size;
        
        glBegin(GL_LINES);
        
        // Bottom face
        glVertex3f(x, y, z);
        glVertex3f(x1, y, z);
        
        glVertex3f(x1, y, z);
        glVertex3f(x1, y, z1);
        
        glVertex3f(x1, y, z1);
        glVertex3f(x, y, z1);
        
        glVertex3f(x, y, z1);
        glVertex3f(x, y, z);
        
        // Top face
        glVertex3f(x, y1, z);
        glVertex3f(x1, y1, z);
        
        glVertex3f(x1, y1, z);
        glVertex3f(x1, y1, z1);
        
        glVertex3f(x1, y1, z1);
        glVertex3f(x, y1, z1);
        
        glVertex3f(x, y1, z1);
        glVertex3f(x, y1, z);
        
        // Vertical edges
        glVertex3f(x, y, z);
        glVertex3f(x, y1, z);
        
        glVertex3f(x1, y, z);
        glVertex3f(x1, y1, z);
        
        glVertex3f(x1, y, z1);
        glVertex3f(x1, y1, z1);
        
        glVertex3f(x, y, z1);
        glVertex3f(x, y1, z1);
        
        glEnd();
    }
}

