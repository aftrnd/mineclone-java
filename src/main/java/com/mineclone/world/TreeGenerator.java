package com.mineclone.world;

import com.mineclone.game.Block;
import com.mineclone.game.ChunkManager;

import java.util.Random;

/**
 * Generates trees similar to Minecraft's oak trees.
 * 
 * Based on Minecraft's tree generation:
 * - Oak tree: trunk 4-6 blocks tall, blob-shaped leaves (radius 2, height 3)
 * - Trees spawn on grass blocks only
 * - Spawn probability: ~5% per chunk (like Minecraft plains biome)
 * 
 * Reference: minecraft:oak configured_feature
 */
public class TreeGenerator {
    // Oak tree parameters (from Minecraft's oak.json)
    private static final int TRUNK_BASE_HEIGHT = 4;
    private static final int TRUNK_HEIGHT_VARIATION = 2;  // Random 0-2 added to base
    private static final int LEAVES_RADIUS = 2;
    private static final int LEAVES_HEIGHT = 3;
    private static final int LEAVES_OFFSET = 0;
    
    /**
     * Try to generate a tree at the specified world position.
     * Returns true if tree was generated.
     * 
     * Minecraft-style: Directly places blocks into chunks during generation,
     * similar to WorldGenRegion pattern.
     * 
     * @param chunkManager Chunk manager to place blocks in
     * @param worldX World X coordinate (center of tree)
     * @param worldY World Y coordinate (base of trunk)
     * @param worldZ World Z coordinate (center of tree)
     * @param random Random generator for tree variation
     * @return true if tree was successfully generated
     */
    public static boolean generateOakTree(ChunkManager chunkManager, int worldX, int worldY, int worldZ, Random random) {
        // Randomize trunk height (4-6 blocks)
        int trunkHeight = TRUNK_BASE_HEIGHT + random.nextInt(TRUNK_HEIGHT_VARIATION + 1);
        
        // Check if there's enough vertical space
        if (worldY + trunkHeight + LEAVES_HEIGHT > 255) {
            return false;  // Too tall for world height
        }
        
        // Check if base block is grass/dirt (like Minecraft's sapling would_survive check)
        Block baseBlock = chunkManager.getBlockAt(worldX, worldY - 1, worldZ);
        if (baseBlock == null) {
            return false;
        }
        if (baseBlock.getType() != Block.Type.GRASS && baseBlock.getType() != Block.Type.DIRT) {
            return false;  // Can't grow here
        }
        
        // Generate trunk (oak logs) - skip lighting updates for performance
        for (int y = 0; y < trunkHeight; y++) {
            chunkManager.placeBlock(worldX, worldY + y, worldZ, Block.Type.OAK_LOG, false);
        }
        
        // Generate leaves (blob-shaped, similar to Minecraft's blob_foliage_placer)
        int leavesStartY = worldY + trunkHeight - 1;  // Leaves start 1 block below top of trunk
        generateBlobLeaves(chunkManager, worldX, leavesStartY, worldZ, LEAVES_RADIUS, LEAVES_HEIGHT);
        
        return true;
    }
    
    /**
     * Generate blob-shaped leaves EXACTLY like Minecraft's BlobFoliagePlacer.
     * Minecraft uses square/diamond patterns, not circles!
     * 
     * Pattern (radius=2, height=3):
     * Layer 0-1 (bottom/middle): Full square (5x5)
     * Layer 2 (top): Smaller square (3x3)
     * Plus single leaf on top center
     */
    private static void generateBlobLeaves(ChunkManager chunkManager, int centerX, int baseY, int centerZ, int radius, int height) {
        // Generate leaves in Minecraft's blob pattern
        // Layers 0 and 1: Full radius
        // Layer 2: Reduced radius (radius - 1)
        
        for (int layerY = 0; layerY < height; layerY++) {
            int currentY = baseY + layerY;
            
            // Minecraft logic: top layer has radius-1, others have full radius
            int layerRadius = (layerY >= height - 1) ? (radius - 1) : radius;
            
            // Generate square/diamond pattern (Minecraft style)
            for (int dx = -layerRadius; dx <= layerRadius; dx++) {
                for (int dz = -layerRadius; dz <= layerRadius; dz++) {
                    // Minecraft uses Chebyshev distance (max of abs values) for square pattern
                    // With a bit of diamond shaping at corners
                    int chebyshevDist = Math.max(Math.abs(dx), Math.abs(dz));
                    int manhattanDist = Math.abs(dx) + Math.abs(dz);
                    
                    // Place if within square bounds, with slightly cut corners for diamond effect
                    boolean shouldPlace = chebyshevDist <= layerRadius && manhattanDist <= layerRadius + 1;
                    
                    if (shouldPlace) {
                        int worldX = centerX + dx;
                        int worldZ = centerZ + dz;
                        
                        // Don't replace solid blocks (like trunk)
                        Block existingBlock = chunkManager.getBlockAt(worldX, currentY, worldZ);
                        if (existingBlock == null || existingBlock.getType() == Block.Type.AIR) {
                            chunkManager.placeBlock(worldX, currentY, worldZ, Block.Type.OAK_LEAVES, false);
                        }
                    }
                }
            }
        }
        
        // Add single top leaf (Minecraft standard)
        int topY = baseY + height;
        chunkManager.placeBlock(centerX, topY, centerZ, Block.Type.OAK_LEAVES, false);
    }
    
    /**
     * Decide if a tree should spawn at this position (for plains biome).
     * Based on Minecraft's trees_plains placed_feature (weighted list: 19 none, 1 tree).
     */
    public static boolean shouldSpawnTree(Random random) {
        // ~5% chance (1 in 20, matching Minecraft's 19:1 weighted list)
        return random.nextInt(20) == 0;
    }
}

