package com.mineclone.world;

import com.mineclone.game.Block;

/**
 * Biome types for terrain generation.
 * Each biome has different terrain characteristics.
 */
public enum Biome {
    PLAINS(
        64,      // Base height
        8,       // Height variation
        Block.Type.GRASS,
        0.8f,    // Temperature
        0.4f     // Moisture
    ),
    FOREST(
        66,      // Slightly higher than plains
        12,      // More variation
        Block.Type.GRASS,
        0.7f,    // Moderate temperature
        0.8f     // High moisture
    ),
    DESERT(
        65,      // Flat-ish
        15,      // Sand dunes
        Block.Type.SAND,
        2.0f,    // Hot
        0.0f     // Dry
    ),
    MOUNTAINS(
        80,      // Much higher base
        40,      // Extreme variation
        Block.Type.STONE,
        0.3f,    // Cold
        0.3f     // Low moisture
    );
    
    private final int baseHeight;
    private final int heightVariation;
    private final Block.Type surfaceBlock;
    private final float temperature;
    private final float moisture;
    
    Biome(int baseHeight, int heightVariation, Block.Type surfaceBlock, float temperature, float moisture) {
        this.baseHeight = baseHeight;
        this.heightVariation = heightVariation;
        this.surfaceBlock = surfaceBlock;
        this.temperature = temperature;
        this.moisture = moisture;
    }
    
    public int getBaseHeight() {
        return baseHeight;
    }
    
    public int getHeightVariation() {
        return heightVariation;
    }
    
    public Block.Type getSurfaceBlock() {
        return surfaceBlock;
    }
    
    public float getTemperature() {
        return temperature;
    }
    
    public float getMoisture() {
        return moisture;
    }
    
    /**
     * Determine biome from temperature and moisture values.
     * Similar to Minecraft's multi-noise biome selection.
     */
    public static Biome fromClimate(double temperature, double moisture) {
        // Normalize to 0-1 range
        temperature = (temperature + 1.0) / 2.0;
        moisture = (moisture + 1.0) / 2.0;
        
        // Simple biome selection based on climate
        if (temperature > 0.8) {
            return DESERT;  // Hot = desert
        } else if (temperature < 0.3) {
            return MOUNTAINS;  // Cold = mountains
        } else if (moisture > 0.6) {
            return FOREST;  // Wet = forest
        } else {
            return PLAINS;  // Default
        }
    }
}

