package com.mineclone.game;

import org.lwjgl.BufferUtils;
import java.nio.ByteBuffer;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL30.glGenerateMipmap;

/**
 * Loads and manages the Steve skin texture for player hand rendering.
 * Minecraft's Steve skin is 64x64 pixels.
 */
public class SteveTexture {
    private int textureId;
    private static final int TEXTURE_SIZE = 64;
    
    /**
     * Create a simple Steve-colored texture (tan/peachy color).
     */
    public void load() {
        // Create a simple solid color texture matching Steve's skin
        ByteBuffer buffer = BufferUtils.createByteBuffer(TEXTURE_SIZE * TEXTURE_SIZE * 4);
        
        // Steve's skin color (peachy/tan)
        byte r = (byte) 242;  // ~0.95
        byte g = (byte) 191;  // ~0.75
        byte b = (byte) 153;  // ~0.60
        byte a = (byte) 255;
        
        // Fill entire texture with skin color
        for (int i = 0; i < TEXTURE_SIZE * TEXTURE_SIZE; i++) {
            buffer.put(r).put(g).put(b).put(a);
        }
        buffer.flip();
        
        // Create OpenGL texture
        textureId = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, textureId);
        
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, TEXTURE_SIZE, TEXTURE_SIZE, 0, GL_RGBA, GL_UNSIGNED_BYTE, buffer);
        glGenerateMipmap(GL_TEXTURE_2D);
        
        // Minecraft-style pixelated rendering
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST_MIPMAP_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_REPEAT);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_REPEAT);
        
        glBindTexture(GL_TEXTURE_2D, 0);
        
        System.out.println("✓ Steve skin texture created (ID: " + textureId + ")");
    }
    
    public void bind() {
        glBindTexture(GL_TEXTURE_2D, textureId);
    }
    
    public void unbind() {
        glBindTexture(GL_TEXTURE_2D, 0);
    }
    
    public void cleanup() {
        glDeleteTextures(textureId);
    }
    
    public int getTextureId() {
        return textureId;
    }
}

