package com.mineclone.world;

import com.mineclone.game.Block;

/**
 * Biome types for terrain generation.
 * Each biome has different terrain characteristics.
 */
public enum Biome {
    PLAINS(
        64,      // Base height
        18,      // INCREASED for rolling hills! (was 8)
        Block.Type.GRASS,
        0.8f,    // Temperature
        0.4f     // Moisture
    ),
    FOREST(
        66,      // Slightly higher than plains
        20,      // More variation (hills)
        Block.Type.GRASS,
        0.7f,    // Moderate temperature
        0.8f     // High moisture
    ),
    DESERT(
        65,      // Flat-ish
        12,      // Sand dunes
        Block.Type.SAND,
        2.0f,    // Hot
        0.0f     // Dry
    ),
    MOUNTAINS(
        110,     // MUCH TALLER! (was 90)
        90,      // EXTREME variation! (was 70, can now reach Y=200!)
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
     * 
     * BALANCED distribution (each biome gets ~25%):
     * - Desert: ~25% (hot & dry)
     * - Mountains: ~25% (cold)
     * - Forest: ~25% (moderate temp, high moisture)
     * - Plains: ~25% (moderate temp, low moisture)
     */
    public static Biome fromClimate(double temperature, double moisture) {
        // Normalize to 0-1 range
        temperature = (temperature + 1.0) / 2.0;
        moisture = (moisture + 1.0) / 2.0;
        
        // More balanced biome selection using quadrants
        if (temperature > 0.6) {
            // Hot half of the world
            if (moisture < 0.5) {
                return DESERT;  // Hot & dry = desert
            } else {
                return PLAINS;  // Hot & wet = plains
            }
        } else if (temperature < 0.4) {
            // Cold half of the world
            if (moisture < 0.5) {
                return MOUNTAINS;  // Cold & dry = mountains
            } else {
                return FOREST;  // Cold & wet = forest (could be taiga later)
            }
        } else {
            // Moderate temperature (40-60% range) - split by moisture
            if (moisture > 0.55) {
                return FOREST;  // Wet = forest
            } else {
                return PLAINS;  // Dry = plains
            }
        }
    }
}

