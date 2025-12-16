package com.mineclone.world;

import com.mineclone.game.Block;

/**
 * Generates terrain using Perlin noise and biomes.
 * Inspired by Minecraft's noise-based generation.
 */
public class TerrainGenerator {
    private final PerlinNoise heightNoise;
    private final PerlinNoise temperatureNoise;
    private final PerlinNoise moistureNoise;
    
    private static final double HEIGHT_SCALE = 0.02;        // Terrain frequency
    private static final double BIOME_SCALE = 0.005;        // Biome frequency (larger = smoother transitions)
    private static final int HEIGHT_OCTAVES = 4;
    private static final double HEIGHT_PERSISTENCE = 0.5;
    
    public TerrainGenerator(long seed) {
        this.heightNoise = new PerlinNoise(seed);
        this.temperatureNoise = new PerlinNoise(seed + 1);
        this.moistureNoise = new PerlinNoise(seed + 2);
        
        System.out.println("TerrainGenerator initialized with seed: " + seed);
    }
    
    /**
     * Get the biome at a world position.
     */
    public Biome getBiome(int worldX, int worldZ) {
        double temperature = temperatureNoise.octaveNoise(worldX, worldZ, 3, 0.5, BIOME_SCALE);
        double moisture = moistureNoise.octaveNoise(worldX, worldZ, 3, 0.5, BIOME_SCALE);
        return Biome.fromClimate(temperature, moisture);
    }
    
    /**
     * Get terrain height at a world position.
     * Returns the Y coordinate of the surface.
     */
    public int getHeight(int worldX, int worldZ) {
        // Get biome to determine height characteristics
        Biome biome = getBiome(worldX, worldZ);
        
        // Generate base terrain height using octave noise
        double noise = heightNoise.octaveNoise(
            worldX, 
            worldZ, 
            HEIGHT_OCTAVES, 
            HEIGHT_PERSISTENCE, 
            HEIGHT_SCALE
        );
        
        // Apply biome-specific height
        int baseHeight = biome.getBaseHeight();
        int variation = (int) (noise * biome.getHeightVariation());
        
        return baseHeight + variation;
    }
    
    /**
     * Get the block type at a specific world position.
     */
    public Block.Type getBlockType(int worldX, int worldY, int worldZ) {
        int surfaceHeight = getHeight(worldX, worldZ);
        
        // Air above surface
        if (worldY > surfaceHeight) {
            return Block.Type.AIR;
        }
        
        // Get biome for surface block type
        Biome biome = getBiome(worldX, worldZ);
        
        // Surface block
        if (worldY == surfaceHeight) {
            return biome.getSurfaceBlock();
        }
        
        // Just below surface (dirt layer in most biomes, except desert/mountains)
        if (worldY > surfaceHeight - 3 && biome != Biome.DESERT && biome != Biome.MOUNTAINS) {
            return Block.Type.DIRT;
        }
        
        // Everything else is stone
        return Block.Type.STONE;
    }
    
    /**
     * Get surface height for player spawning.
     * Returns a good spawn position (preferably plains).
     */
    public int getSurfaceHeight(int worldX, int worldZ) {
        return getHeight(worldX, worldZ);
    }
}

