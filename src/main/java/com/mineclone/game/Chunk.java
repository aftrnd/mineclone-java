package com.mineclone.game;

import java.util.*;

/**
 * A 16x16xN chunk of blocks.
 * 
 * Minecraft-style chunk structure:
 * - 16x16 horizontal area (X and Z)
 * - Variable height (Y) - currently 256 blocks
 * - Stores blocks in [x][y][z] array
 * - Mesh generated on-demand for rendering
 * 
 * Coordinate system:
 * - X/Z: Local coordinates within chunk (0-15)
 * - Y: Absolute world height (0-HEIGHT)
 * - World coordinates = chunk position * 16 + local coordinates
 * 
 * Optimizations:
 * - Only visible block faces are added to mesh
 * - Mesh is cached and regenerated when blocks change
 * - Empty chunks can skip rendering entirely
 */
public class Chunk {
    public static final int SIZE = 16;      // Horizontal size (X and Z)
    public static final int HEIGHT = 256;   // Vertical size (Y) - Minecraft standard
    
    // Chunk position (in chunk coordinates, not world)
    public final int chunkX;
    public final int chunkZ;
    
    // Block storage [x][y][z]
    private final Block[][][] blocks;
    
    // Mesh data (cached for rendering)
    private List<float[]> meshData;
    private boolean meshDirty;  // True if mesh needs regeneration

    /**
     * Create a new chunk at the given chunk coordinates.
     * @param chunkX Chunk X coordinate (world X / 16)
     * @param chunkZ Chunk Z coordinate (world Z / 16)
     */
    public Chunk(int chunkX, int chunkZ) {
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
        this.blocks = new Block[SIZE][HEIGHT][SIZE];
        this.meshData = new ArrayList<>();
        this.meshDirty = true;

        // Initialize with air
        for (int x = 0; x < SIZE; x++) {
            for (int y = 0; y < HEIGHT; y++) {
                for (int z = 0; z < SIZE; z++) {
                    blocks[x][y][z] = new Block(Block.Type.AIR);
                }
            }
        }
    }
    
    /**
     * Get chunk position as ChunkPos.
     */
    public ChunkPos getPos() {
        return new ChunkPos(chunkX, chunkZ);
    }

    /**
     * Set a block at local chunk coordinates.
     * Marks mesh as dirty for regeneration.
     */
    public void setBlock(int x, int y, int z, Block.Type type) {
        if (x >= 0 && x < SIZE && y >= 0 && y < HEIGHT && z >= 0 && z < SIZE) {
            blocks[x][y][z] = new Block(type);
            meshDirty = true;  // Mark for mesh regeneration
        }
    }

    /**
     * Get block at local chunk coordinates.
     * Returns AIR if out of bounds.
     */
    public Block getBlock(int x, int y, int z) {
        if (x >= 0 && x < SIZE && y >= 0 && y < HEIGHT && z >= 0 && z < SIZE) {
            return blocks[x][y][z];
        }
        return new Block(Block.Type.AIR);
    }

    /**
     * Check if block is solid at local coordinates.
     */
    public boolean isSolid(int x, int y, int z) {
        return getBlock(x, y, z).isSolid();
    }
    
    /**
     * Check if this chunk is empty (all air).
     * Empty chunks can skip rendering.
     */
    public boolean isEmpty() {
        for (int x = 0; x < SIZE; x++) {
            for (int y = 0; y < HEIGHT; y++) {
                for (int z = 0; z < SIZE; z++) {
                    if (blocks[x][y][z].isSolid()) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    /**
     * Get the highest solid block in a column (for terrain height).
     * Returns -1 if column is empty.
     */
    public int getHeightAt(int x, int z) {
        if (x < 0 || x >= SIZE || z < 0 || z >= SIZE) {
            return -1;
        }
        
        // Search from top down
        for (int y = HEIGHT - 1; y >= 0; y--) {
            if (blocks[x][y][z].isSolid()) {
                return y;
            }
        }
        return -1;  // No solid blocks in column
    }

    /**
     * Generate mesh for rendering.
     * Only generates if mesh is dirty (blocks changed).
     * Returns vertex data as float array.
     */
    public float[] generateMesh() {
        // Only regenerate if mesh is dirty
        if (!meshDirty && !meshData.isEmpty()) {
            // Convert cached mesh to array
            return convertMeshToArray();
        }
        
        meshData.clear();

        // Generate cube mesh for each solid block
        // Only add visible faces (face culling optimization)
        for (int x = 0; x < SIZE; x++) {
            for (int y = 0; y < HEIGHT; y++) {
                for (int z = 0; z < SIZE; z++) {
                    Block block = blocks[x][y][z];
                    if (block.isSolid()) {
                        addCubeMesh(x, y, z, block.getType());
                    }
                }
            }
        }

        meshDirty = false;  // Mark as clean
        return convertMeshToArray();
    }
    
    /**
     * Convert mesh data list to float array.
     * Each vertex has 6 floats: position (x,y,z) + color (r,g,b)
     */
    private float[] convertMeshToArray() {
        float[] vertices = new float[meshData.size() * 6];
        for (int i = 0; i < meshData.size(); i++) {
            float[] vertex = meshData.get(i);
            // Position
            vertices[i * 6 + 0] = vertex[0];  // x
            vertices[i * 6 + 1] = vertex[1];  // y
            vertices[i * 6 + 2] = vertex[2];  // z
            // Color
            vertices[i * 6 + 3] = vertex[3];  // r
            vertices[i * 6 + 4] = vertex[4];  // g
            vertices[i * 6 + 5] = vertex[5];  // b
        }
        return vertices;
    }

    /**
     * Add a cube mesh for a single block.
     * Only adds visible faces (face culling optimization).
     */
    private void addCubeMesh(int x, int y, int z, Block.Type type) {
        float size = 1.0f;

        // Check which faces are visible (not covered by other solid blocks)
        boolean topVisible = !getBlock(x, y + 1, z).isSolid();
        boolean bottomVisible = !getBlock(x, y - 1, z).isSolid();
        boolean northVisible = !getBlock(x, y, z + 1).isSolid();
        boolean southVisible = !getBlock(x, y, z - 1).isSolid();
        boolean eastVisible = !getBlock(x + 1, y, z).isSolid();
        boolean westVisible = !getBlock(x - 1, y, z).isSolid();

        // Get color for each face type
        float[] topColor = type.getColor(true, false);
        float[] bottomColor = type.getColor(false, true);
        float[] sideColor = type.getColor(false, false);
        
        // Add vertices for visible faces only (proper cube faces)
        if (topVisible) addTopFace(x, y, z, size, topColor);
        if (bottomVisible) addBottomFace(x, y, z, size, bottomColor);
        if (northVisible) addNorthFace(x, y, z, size, sideColor);
        if (southVisible) addSouthFace(x, y, z, size, sideColor);
        if (eastVisible) addEastFace(x, y, z, size, sideColor);
        if (westVisible) addWestFace(x, y, z, size, sideColor);
    }

    // Top face (Y+)
    private void addTopFace(float x, float y, float z, float size, float[] color) {
        float x0 = x, x1 = x + size;
        float y1 = y + size;
        float z0 = z, z1 = z + size;
        
        addQuad(
            x0, y1, z1, color,  // Front-left
            x1, y1, z1, color,  // Front-right
            x1, y1, z0, color,  // Back-right
            x0, y1, z0, color   // Back-left
        );
    }

    // Bottom face (Y-)
    private void addBottomFace(float x, float y, float z, float size, float[] color) {
        float x0 = x, x1 = x + size;
        float y0 = y;
        float z0 = z, z1 = z + size;
        
        addQuad(
            x0, y0, z0, color,  // Back-left
            x1, y0, z0, color,  // Back-right
            x1, y0, z1, color,  // Front-right
            x0, y0, z1, color   // Front-left
        );
    }

    // North face (Z+)
    private void addNorthFace(float x, float y, float z, float size, float[] color) {
        float x0 = x, x1 = x + size;
        float y0 = y, y1 = y + size;
        float z1 = z + size;
        
        addQuad(
            x0, y0, z1, color,  // Bottom-left
            x1, y0, z1, color,  // Bottom-right
            x1, y1, z1, color,  // Top-right
            x0, y1, z1, color   // Top-left
        );
    }

    // South face (Z-)
    private void addSouthFace(float x, float y, float z, float size, float[] color) {
        float x0 = x, x1 = x + size;
        float y0 = y, y1 = y + size;
        float z0 = z;
        
        addQuad(
            x1, y0, z0, color,  // Bottom-right
            x0, y0, z0, color,  // Bottom-left
            x0, y1, z0, color,  // Top-left
            x1, y1, z0, color   // Top-right
        );
    }

    // East face (X+)
    private void addEastFace(float x, float y, float z, float size, float[] color) {
        float x1 = x + size;
        float y0 = y, y1 = y + size;
        float z0 = z, z1 = z + size;
        
        addQuad(
            x1, y0, z1, color,  // Bottom-front
            x1, y0, z0, color,  // Bottom-back
            x1, y1, z0, color,  // Top-back
            x1, y1, z1, color   // Top-front
        );
    }

    // West face (X-)
    private void addWestFace(float x, float y, float z, float size, float[] color) {
        float x0 = x;
        float y0 = y, y1 = y + size;
        float z0 = z, z1 = z + size;
        
        addQuad(
            x0, y0, z0, color,  // Bottom-back
            x0, y0, z1, color,  // Bottom-front
            x0, y1, z1, color,  // Top-front
            x0, y1, z0, color   // Top-back
        );
    }

    /**
     * Add a quad (4 vertices) as two triangles.
     * Vertices should be in counter-clockwise order when viewed from outside.
     */
    private void addQuad(float x1, float y1, float z1, float[] c1,
                        float x2, float y2, float z2, float[] c2,
                        float x3, float y3, float z3, float[] c3,
                        float x4, float y4, float z4, float[] c4) {
        // First triangle (v1, v2, v3)
        meshData.add(new float[]{x1, y1, z1, c1[0], c1[1], c1[2]});
        meshData.add(new float[]{x2, y2, z2, c2[0], c2[1], c2[2]});
        meshData.add(new float[]{x3, y3, z3, c3[0], c3[1], c3[2]});

        // Second triangle (v1, v3, v4)
        meshData.add(new float[]{x1, y1, z1, c1[0], c1[1], c1[2]});
        meshData.add(new float[]{x3, y3, z3, c3[0], c3[1], c3[2]});
        meshData.add(new float[]{x4, y4, z4, c4[0], c4[1], c4[2]});
    }
    
    /**
     * Mark mesh as dirty (needs regeneration).
     * Call this when blocks change.
     */
    public void markDirty() {
        this.meshDirty = true;
    }
    
    /**
     * Check if mesh needs regeneration.
     */
    public boolean isDirty() {
        return meshDirty;
    }
}


