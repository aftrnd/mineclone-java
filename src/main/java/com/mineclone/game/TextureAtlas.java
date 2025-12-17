package com.mineclone.game;

import com.mineclone.core.ResourceLoader;
import org.lwjgl.BufferUtils;
import org.lwjgl.stb.STBImage;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL30.glGenerateMipmap;

/**
 * Manages block textures for the game.
 * 
 * Creates a simple texture atlas from individual block textures.
 * Currently supports: grass_top, grass_side, dirt, stone, oak_log_top, oak_log_side, oak_leaves, sand
 * 
 * Atlas Layout (4x2 grid = 8 textures):
 * Row 0: [0] grass_top    [1] grass_side  [2] dirt          [3] stone
 * Row 1: [4] oak_log_top  [5] oak_log_side [6] oak_leaves   [7] sand
 */
public class TextureAtlas {
    private static final int ATLAS_WIDTH_TILES = 4;  // 4 tiles wide
    private static final int ATLAS_HEIGHT_TILES = 2;  // 2 tiles tall
    private static final int TEXTURE_SIZE = 16;  // Minecraft textures are 16x16
    private static final int ATLAS_WIDTH = ATLAS_WIDTH_TILES * TEXTURE_SIZE;  // 64
    private static final int ATLAS_HEIGHT = ATLAS_HEIGHT_TILES * TEXTURE_SIZE; // 32
    
    // Texture indices in atlas
    public static final int TEX_GRASS_TOP = 0;
    public static final int TEX_GRASS_SIDE = 1;
    public static final int TEX_DIRT = 2;
    public static final int TEX_STONE = 3;
    public static final int TEX_OAK_LOG_TOP = 4;
    public static final int TEX_OAK_LOG_SIDE = 5;
    public static final int TEX_OAK_LEAVES = 6;
    public static final int TEX_SAND = 7;
    
    private int textureId;
    
    /**
     * Load and create texture atlas.
     */
    public void load() {
        System.out.println("Loading texture atlas...");
        
        // Create atlas buffer (RGBA = 4 bytes per pixel)
        ByteBuffer atlasBuffer = BufferUtils.createByteBuffer(ATLAS_WIDTH * ATLAS_HEIGHT * 4);
        
        // Load individual textures and place them in atlas
        // Row 0
        loadTextureToAtlas(atlasBuffer, "/textures/block/grass_block_top.png", 0, 0);
        loadTextureToAtlas(atlasBuffer, "/textures/block/grass_block_side.png", 1, 0);
        loadTextureToAtlas(atlasBuffer, "/textures/block/dirt.png", 2, 0);
        loadTextureToAtlas(atlasBuffer, "/textures/block/stone.png", 3, 0);
        // Row 1
        loadTextureToAtlas(atlasBuffer, "/textures/block/oak_log_top.png", 0, 1);
        loadTextureToAtlas(atlasBuffer, "/textures/block/oak_log_side.png", 1, 1);
        loadTextureToAtlas(atlasBuffer, "/textures/block/oak_leaves.png", 2, 1);
        loadTextureToAtlas(atlasBuffer, "/textures/block/sand.png", 3, 1);  // Real sand texture!
        
        // Create OpenGL texture
        textureId = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, textureId);
        
        // Upload atlas to GPU
        atlasBuffer.flip();
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, ATLAS_WIDTH, ATLAS_HEIGHT, 0, GL_RGBA, GL_UNSIGNED_BYTE, atlasBuffer);
        
        // Generate mipmaps for better quality at distance
        glGenerateMipmap(GL_TEXTURE_2D);
        
        // Set texture parameters (Minecraft-style pixelated look)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST_MIPMAP_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_REPEAT);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_REPEAT);
        
        glBindTexture(GL_TEXTURE_2D, 0);
        
        System.out.println("✓ Texture atlas created: " + ATLAS_WIDTH + "x" + ATLAS_HEIGHT + " (ID: " + textureId + ")");
    }
    
    /**
     * Load a texture from resources and place it in the atlas.
     */
    private void loadTextureToAtlas(ByteBuffer atlasBuffer, String path, int gridX, int gridY) {
        try {
            IntBuffer width = BufferUtils.createIntBuffer(1);
            IntBuffer height = BufferUtils.createIntBuffer(1);
            IntBuffer channels = BufferUtils.createIntBuffer(1);
            
            // Load image from classpath resources
            java.io.InputStream inputStream = getClass().getResourceAsStream(path);
            if (inputStream == null) {
                throw new RuntimeException("Failed to find texture: " + path);
            }
            
            // Read all bytes from input stream into a ByteBuffer
            byte[] imageBytes = inputStream.readAllBytes();
            inputStream.close();
            ByteBuffer imageBuffer = BufferUtils.createByteBuffer(imageBytes.length);
            imageBuffer.put(imageBytes);
            imageBuffer.flip();
            
            // Decode image with STB
            ByteBuffer imageData = STBImage.stbi_load_from_memory(imageBuffer, width, height, channels, 4);
            
            if (imageData == null) {
                throw new RuntimeException("Failed to load texture: " + path + " - " + STBImage.stbi_failure_reason());
            }
            
            int texWidth = width.get(0);
            int texHeight = height.get(0);
            
            if (texWidth != TEXTURE_SIZE || texHeight != TEXTURE_SIZE) {
                System.err.println("Warning: Texture " + path + " is " + texWidth + "x" + texHeight + ", expected " + TEXTURE_SIZE + "x" + TEXTURE_SIZE);
            }
            
            // Copy texture data into atlas at specified grid position
            int atlasX = gridX * TEXTURE_SIZE;
            int atlasY = gridY * TEXTURE_SIZE;
            
            for (int y = 0; y < TEXTURE_SIZE && y < texHeight; y++) {
                for (int x = 0; x < TEXTURE_SIZE && x < texWidth; x++) {
                    int srcIndex = (y * texWidth + x) * 4;
                    int dstIndex = ((atlasY + y) * ATLAS_WIDTH + (atlasX + x)) * 4;
                    
                    // Copy RGBA
                    atlasBuffer.put(dstIndex + 0, imageData.get(srcIndex + 0));  // R
                    atlasBuffer.put(dstIndex + 1, imageData.get(srcIndex + 1));  // G
                    atlasBuffer.put(dstIndex + 2, imageData.get(srcIndex + 2));  // B
                    atlasBuffer.put(dstIndex + 3, imageData.get(srcIndex + 3));  // A
                }
            }
            
            STBImage.stbi_image_free(imageData);
            System.out.println("  Loaded: " + path + " at [" + gridX + "," + gridY + "]");
            
        } catch (Exception e) {
            System.err.println("Error loading texture " + path + ": " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Get UV coordinates for a texture in the atlas.
     * Returns [u_min, v_min, u_max, v_max]
     */
    public float[] getUVs(int textureIndex) {
        int gridX = textureIndex % ATLAS_WIDTH_TILES;
        int gridY = textureIndex / ATLAS_WIDTH_TILES;
        
        float uMin = (float) gridX / ATLAS_WIDTH_TILES;
        float vMin = (float) gridY / ATLAS_HEIGHT_TILES;
        float uMax = (float) (gridX + 1) / ATLAS_WIDTH_TILES;
        float vMax = (float) (gridY + 1) / ATLAS_HEIGHT_TILES;
        
        return new float[] { uMin, vMin, uMax, vMax };
    }
    
    /**
     * Bind the texture atlas for rendering.
     */
    public void bind() {
        glBindTexture(GL_TEXTURE_2D, textureId);
    }
    
    /**
     * Unbind texture.
     */
    public void unbind() {
        glBindTexture(GL_TEXTURE_2D, 0);
    }
    
    /**
     * Clean up texture resources.
     */
    public void cleanup() {
        glDeleteTextures(textureId);
    }
    
    public int getTextureId() {
        return textureId;
    }
}

