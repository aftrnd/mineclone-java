package com.mineclone.game;

/**
 * Immutable chunk coordinate holder.
 * Chunks are 16x16 in the XZ plane.
 * 
 * Following Minecraft's coordinate system:
 * - Chunk coordinates are world coordinates divided by 16 (right shift 4)
 * - Converting back: worldX = chunkX * 16 + localX
 */
public record ChunkPos(int x, int z) {
    
    /**
     * Create ChunkPos from world coordinates.
     */
    public static ChunkPos fromWorldPos(int worldX, int worldZ) {
        return new ChunkPos(worldX >> 4, worldZ >> 4);
    }
    
    /**
     * Create ChunkPos from world coordinates (float).
     */
    public static ChunkPos fromWorldPos(float worldX, float worldZ) {
        return fromWorldPos((int) Math.floor(worldX), (int) Math.floor(worldZ));
    }
    
    /**
     * Get the world X coordinate of the chunk's corner (minimum X).
     */
    public int getWorldX() {
        return x << 4; // x * 16
    }
    
    /**
     * Get the world Z coordinate of the chunk's corner (minimum Z).
     */
    public int getWorldZ() {
        return z << 4; // z * 16
    }
    
    /**
     * Get the center world X coordinate of the chunk.
     */
    public float getCenterX() {
        return (x << 4) + 8.0f;
    }
    
    /**
     * Get the center world Z coordinate of the chunk.
     */
    public float getCenterZ() {
        return (z << 4) + 8.0f;
    }
    
    /**
     * Calculate distance squared to a world position.
     * Useful for sorting chunks by distance.
     */
    public float distanceSquared(float worldX, float worldZ) {
        float dx = getCenterX() - worldX;
        float dz = getCenterZ() - worldZ;
        return dx * dx + dz * dz;
    }
    
    @Override
    public String toString() {
        return "[" + x + ", " + z + "]";
    }
    
    /**
     * Generate a unique long key for this chunk position.
     * Useful for HashMap keys (more efficient than ChunkPos objects).
     */
    public long toLong() {
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
    }
    
    /**
     * Create ChunkPos from a long key.
     */
    public static ChunkPos fromLong(long key) {
        return new ChunkPos((int) (key >> 32), (int) key);
    }
}

