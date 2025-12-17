package com.mineclone.game;

import com.mineclone.core.utils.Logger;
import org.lwjgl.BufferUtils;
import org.lwjgl.stb.STBImage;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;

/**
 * Minecraft's biome colormap system (from ColorMapColorUtil.java & GrassColor.java).
 * 
 * Loads 256x256 colormap textures and samples based on biome temperature and rainfall.
 * 
 * Algorithm:
 * 1. rain = rain * temperature
 * 2. x = (1.0 - temperature) * 255
 * 3. y = (1.0 - rain) * 255
 * 4. return pixels[y * 256 + x]
 */
public class BiomeColorMap {
    private static final String CATEGORY = "BiomeColorMap";
    private static final int COLORMAP_SIZE = 256;
    
    private int[] grassPixels = new int[COLORMAP_SIZE * COLORMAP_SIZE];
    private int[] foliagePixels = new int[COLORMAP_SIZE * COLORMAP_SIZE];
    
    /**
     * Load colormap textures from resources.
     */
    public void load() {
        Logger.info(CATEGORY, "Loading biome colormaps...");
        
        grassPixels = loadColorMap("/textures/colormap/grass.png");
        foliagePixels = loadColorMap("/textures/colormap/foliage.png");
        
        Logger.info(CATEGORY, "✓ Biome colormaps loaded");
    }
    
    /**
     * Load a colormap PNG into an int array.
     */
    private int[] loadColorMap(String path) {
        try {
            IntBuffer width = BufferUtils.createIntBuffer(1);
            IntBuffer height = BufferUtils.createIntBuffer(1);
            IntBuffer channels = BufferUtils.createIntBuffer(1);
            
            // Load image from classpath
            java.io.InputStream inputStream = getClass().getResourceAsStream(path);
            if (inputStream == null) {
                Logger.error(CATEGORY, "Failed to find colormap: " + path);
                return createDefaultColorMap();
            }
            
            byte[] imageBytes = inputStream.readAllBytes();
            inputStream.close();
            ByteBuffer imageBuffer = BufferUtils.createByteBuffer(imageBytes.length);
            imageBuffer.put(imageBytes);
            imageBuffer.flip();
            
            // Decode image with STB (request RGBA)
            ByteBuffer imageData = STBImage.stbi_load_from_memory(imageBuffer, width, height, channels, 4);
            
            if (imageData == null) {
                Logger.error(CATEGORY, "Failed to load colormap: " + path + " - " + STBImage.stbi_failure_reason());
                return createDefaultColorMap();
            }
            
            int w = width.get(0);
            int h = height.get(0);
            
            if (w != COLORMAP_SIZE || h != COLORMAP_SIZE) {
                Logger.warn(CATEGORY, "Colormap " + path + " is " + w + "x" + h + ", expected 256x256");
            }
            
            // Convert RGBA bytes to int array (0xAARRGGBB format)
            int[] pixels = new int[COLORMAP_SIZE * COLORMAP_SIZE];
            for (int y = 0; y < COLORMAP_SIZE && y < h; y++) {
                for (int x = 0; x < COLORMAP_SIZE && x < w; x++) {
                    int srcIndex = (y * w + x) * 4;
                    int r = imageData.get(srcIndex) & 0xFF;
                    int g = imageData.get(srcIndex + 1) & 0xFF;
                    int b = imageData.get(srcIndex + 2) & 0xFF;
                    int a = imageData.get(srcIndex + 3) & 0xFF;
                    
                    pixels[y * COLORMAP_SIZE + x] = (a << 24) | (r << 16) | (g << 8) | b;
                }
            }
            
            STBImage.stbi_image_free(imageData);
            Logger.debug(CATEGORY, "Loaded colormap: " + path);
            
            return pixels;
            
        } catch (Exception e) {
            Logger.error(CATEGORY, "Error loading colormap " + path, e);
            return createDefaultColorMap();
        }
    }
    
    /**
     * Create a default green colormap if loading fails.
     */
    private int[] createDefaultColorMap() {
        int[] pixels = new int[COLORMAP_SIZE * COLORMAP_SIZE];
        int defaultGreen = 0xFF48B518;  // Minecraft's default grass green
        for (int i = 0; i < pixels.length; i++) {
            pixels[i] = defaultGreen;
        }
        return pixels;
    }
    
    /**
     * Get grass color based on biome temperature and rainfall.
     * Based on GrassColor.get() and ColorMapColorUtil.get()
     * 
     * @param temperature 0.0 (cold) to 1.0 (hot)
     * @param rainfall 0.0 (dry) to 1.0 (wet)
     * @return ARGB color
     */
    public int getGrassColor(double temperature, double rainfall) {
        return sampleColorMap(grassPixels, temperature, rainfall, 0xFF48B518);
    }
    
    /**
     * Get foliage color based on biome temperature and rainfall.
     * 
     * @param temperature 0.0 (cold) to 1.0 (hot)
     * @param rainfall 0.0 (dry) to 1.0 (wet)
     * @return ARGB color
     */
    public int getFoliageColor(double temperature, double rainfall) {
        return sampleColorMap(foliagePixels, temperature, rainfall, 0xFF48B518);
    }
    
    /**
     * Minecraft's colormap sampling algorithm (ColorMapColorUtil.get).
     */
    private int sampleColorMap(int[] pixels, double temp, double rain, int defaultColor) {
        // Minecraft's algorithm
        rain *= temp;  // Adjust rainfall by temperature
        int x = (int)((1.0 - temp) * 255.0);
        int y = (int)((1.0 - rain) * 255.0);
        int index = y * COLORMAP_SIZE + x;
        
        return index >= 0 && index < pixels.length ? pixels[index] : defaultColor;
    }
    
    /**
     * Get default grass color (Minecraft uses temp=0.5, rain=1.0).
     */
    public int getDefaultGrassColor() {
        return getGrassColor(0.5, 1.0);
    }
    
    /**
     * Get default foliage color.
     */
    public int getDefaultFoliageColor() {
        return getFoliageColor(0.5, 1.0);
    }
    
    /**
     * Convert ARGB int to RGB float array (0.0-1.0).
     */
    public static float[] argbToRGB(int argb) {
        int r = (argb >> 16) & 0xFF;
        int g = (argb >> 8) & 0xFF;
        int b = argb & 0xFF;
        return new float[]{r / 255.0f, g / 255.0f, b / 255.0f};
    }
}

