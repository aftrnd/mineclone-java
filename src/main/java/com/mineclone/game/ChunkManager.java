package com.mineclone.game;

import com.mineclone.world.TerrainGenerator;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages multiple chunks in the world.
 * Handles chunk loading, unloading, and coordinate lookups.
 * 
 * Based on Minecraft's chunk management but simplified:
 * - No async generation (for now)
 * - Simple radius-based loading
 * - Flat terrain generation only
 * 
 * Future improvements:
 * - Async chunk generation
 * - Chunk saving/loading
 * - Progressive mesh generation
 * - LOD (Level of Detail) system
 */
public class ChunkManager {
    // Chunk storage (using long keys for efficiency)
    private final Map<Long, Chunk> chunks;
    
    // Terrain generation
    private final TerrainGenerator terrainGenerator;
    
    // Render distance (in chunks)
    private int renderDistance;
    
    // Statistics
    private int chunksLoaded;
    private int chunksGenerated;

    /**
     * Create a new chunk manager with terrain generation.
     * @param renderDistance How many chunks to load around the player
     * @param seed World seed for terrain generation
     */
    public ChunkManager(int renderDistance, long seed) {
        this.chunks = new ConcurrentHashMap<>();
        this.renderDistance = renderDistance;
        this.terrainGenerator = new TerrainGenerator(seed);
        this.chunksLoaded = 0;
        this.chunksGenerated = 0;
    }

    /**
     * Get or generate a chunk at the given position.
     */
    public Chunk getChunk(ChunkPos pos) {
        return getChunk(pos.x(), pos.z());
    }

    /**
     * Get or generate a chunk at the given chunk coordinates.
     */
    public Chunk getChunk(int chunkX, int chunkZ) {
        long key = new ChunkPos(chunkX, chunkZ).toLong();
        
        return chunks.computeIfAbsent(key, k -> {
            Chunk chunk = new Chunk(chunkX, chunkZ);
            generateTerrain(chunk);
            chunksGenerated++;
            chunksLoaded++;
            return chunk;
        });
    }

    /**
     * Get chunk from world coordinates.
     */
    public Chunk getChunkFromWorldPos(float worldX, float worldZ) {
        ChunkPos pos = ChunkPos.fromWorldPos(worldX, worldZ);
        return getChunk(pos);
    }

    /**
     * Get a block at world coordinates.
     * Returns null if chunk not loaded.
     */
    public Block getBlockAt(int worldX, int worldY, int worldZ) {
        ChunkPos pos = ChunkPos.fromWorldPos(worldX, worldZ);
        Chunk chunk = chunks.get(pos.toLong());
        
        if (chunk == null) {
            return null;
        }
        
        // Convert to local coordinates
        int localX = worldX & 15; // Fast modulo 16
        int localZ = worldZ & 15;
        
        return chunk.getBlock(localX, worldY, localZ);
    }

    /**
     * Check if a block is solid at world coordinates.
     */
    public boolean isBlockSolidAt(int worldX, int worldY, int worldZ) {
        Block block = getBlockAt(worldX, worldY, worldZ);
        return block != null && block.isSolid();
    }
    
    /**
     * Break (remove) a block at world coordinates.
     * Sets the block to AIR and marks the chunk as dirty.
     */
    public void breakBlock(int worldX, int worldY, int worldZ) {
        ChunkPos pos = ChunkPos.fromWorldPos(worldX, worldZ);
        Chunk chunk = chunks.get(pos.toLong());
        
        if (chunk != null) {
            int localX = worldX & 15;
            int localZ = worldZ & 15;
            
            chunk.setBlock(localX, worldY, localZ, Block.Type.AIR);
            chunk.markDirty();  // Force mesh regeneration
            
            System.out.println("Block broken at (" + worldX + ", " + worldY + ", " + worldZ + ")");
        }
    }
    
    /**
     * Place a block at world coordinates.
     * Sets the block to the specified type and marks the chunk as dirty.
     */
    public void placeBlock(int worldX, int worldY, int worldZ, Block.Type blockType) {
        ChunkPos pos = ChunkPos.fromWorldPos(worldX, worldZ);
        Chunk chunk = chunks.get(pos.toLong());
        
        if (chunk != null) {
            int localX = worldX & 15;
            int localZ = worldZ & 15;
            
            // Don't place blocks outside valid range
            if (worldY < 0 || worldY >= Chunk.HEIGHT) {
                return;
            }
            
            chunk.setBlock(localX, worldY, localZ, blockType);
            chunk.markDirty();  // Force mesh regeneration
            
            System.out.println("Block placed at (" + worldX + ", " + worldY + ", " + worldZ + "): " + blockType);
        }
    }

    /**
     * Update loaded chunks based on player position.
     * Loads new chunks in range, unloads far chunks.
     */
    public void updateLoadedChunks(float playerX, float playerZ) {
        ChunkPos playerChunk = ChunkPos.fromWorldPos(playerX, playerZ);
        
        // Load chunks in render distance
        for (int x = -renderDistance; x <= renderDistance; x++) {
            for (int z = -renderDistance; z <= renderDistance; z++) {
                // Only load chunks within circular radius
                if (x * x + z * z <= renderDistance * renderDistance) {
                    getChunk(playerChunk.x() + x, playerChunk.z() + z);
                }
            }
        }
        
        // Unload far chunks (simple strategy: unload if > 2x render distance)
        int unloadDistance = renderDistance * 2;
        List<Long> toUnload = new ArrayList<>();
        
        for (Map.Entry<Long, Chunk> entry : chunks.entrySet()) {
            ChunkPos pos = ChunkPos.fromLong(entry.getKey());
            int dx = pos.x() - playerChunk.x();
            int dz = pos.z() - playerChunk.z();
            
            if (dx * dx + dz * dz > unloadDistance * unloadDistance) {
                toUnload.add(entry.getKey());
            }
        }
        
        // Unload chunks
        for (Long key : toUnload) {
            chunks.remove(key);
            chunksLoaded--;
        }
    }

    /**
     * Generate terrain for a chunk using the terrain generator.
     */
    private void generateTerrain(Chunk chunk) {
        // Convert chunk coordinates to world coordinates
        int chunkWorldX = chunk.chunkX * Chunk.SIZE;
        int chunkWorldZ = chunk.chunkZ * Chunk.SIZE;
        
        // Generate terrain for each column in the chunk
        for (int localX = 0; localX < Chunk.SIZE; localX++) {
            for (int localZ = 0; localZ < Chunk.SIZE; localZ++) {
                int worldX = chunkWorldX + localX;
                int worldZ = chunkWorldZ + localZ;
                
                // Generate entire column
                for (int y = 0; y < Chunk.HEIGHT; y++) {
                    Block.Type blockType = terrainGenerator.getBlockType(worldX, y, worldZ);
                    
                    if (blockType != Block.Type.AIR) {
                        chunk.setBlock(localX, y, localZ, blockType);
                    }
                }
            }
        }
    }
    
    /**
     * Get surface height at world coordinates for player spawning.
     */
    public int getSurfaceHeight(int worldX, int worldZ) {
        return terrainGenerator.getSurfaceHeight(worldX, worldZ);
    }

    /**
     * Get all loaded chunks (for rendering).
     */
    public Collection<Chunk> getLoadedChunks() {
        return chunks.values();
    }

    /**
     * Get number of loaded chunks.
     */
    public int getLoadedChunkCount() {
        return chunksLoaded;
    }

    /**
     * Get total chunks generated (including unloaded).
     */
    public int getGeneratedChunkCount() {
        return chunksGenerated;
    }

    /**
     * Set render distance and trigger chunk updates.
     */
    public void setRenderDistance(int distance) {
        this.renderDistance = Math.max(1, Math.min(distance, 32)); // Clamp 1-32
    }

    /**
     * Get render distance.
     */
    public int getRenderDistance() {
        return renderDistance;
    }

    /**
     * Clear all chunks (for world reload).
     */
    public void clear() {
        chunks.clear();
        chunksLoaded = 0;
        // Don't reset chunksGenerated (lifetime stat)
    }
}

