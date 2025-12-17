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
 * Lighting (Minecraft 1.21.11):
 * - Face brightness multipliers (ambient occlusion)
 * - Per-vertex AO from adjacent solid blocks
 * - Smooth lighting calculated at mesh generation
 * 
 * Optimizations:
 * - Only visible block faces are added to mesh
 * - Mesh is cached and regenerated when blocks change
 * - Empty chunks can skip rendering entirely
 */
public class Chunk {
    public static final int SIZE = 16;      // Horizontal size (X and Z)
    public static final int HEIGHT = 256;   // Vertical size (Y) - Minecraft standard
    
    // Minecraft's exact face brightness multipliers (from shader analysis)
    // These create the characteristic "smooth lighting" appearance
    private static final float BRIGHTNESS_TOP = 1.0f;     // Y+ (brightest)
    private static final float BRIGHTNESS_BOTTOM = 0.5f;  // Y- (darkest)
    private static final float BRIGHTNESS_NORTH = 0.8f;   // Z-
    private static final float BRIGHTNESS_SOUTH = 0.8f;   // Z+
    private static final float BRIGHTNESS_EAST = 0.6f;    // X+
    private static final float BRIGHTNESS_WEST = 0.6f;    // X-
    
    // Ambient occlusion strength (maximum darkening from solid neighbors)
    private static final float AO_STRENGTH = 0.4f;  // 40% darkening max
    
    // Minecraft's lighting constants (from light.glsl shader)
    // These ensure textures never get too dark, even in shadows or caves
    private static final float MINECRAFT_LIGHT_POWER = 0.6f;   // 60% contribution from lighting
    private static final float MINECRAFT_AMBIENT_LIGHT = 0.4f; // 40% minimum ambient light
    
    // Chunk position (in chunk coordinates, not world)
    public final int chunkX;
    public final int chunkZ;
    
    // Block storage [x][y][z]
    private final Block[][][] blocks;
    
    // Mesh data (cached for rendering)
    private List<float[]> meshData;
    private boolean meshDirty;  // True if mesh needs regeneration
    private boolean treesGenerated = false;  // Track if trees have been placed
    
    // GPU mesh cache (Minecraft-style: one VBO per chunk)
    private int vboId = -1;  // OpenGL VBO handle
    private int vertexCount = 0;  // Number of vertices in this chunk's mesh
    private boolean vboNeedsUpdate = true;  // True if VBO needs reuploading
    private volatile boolean meshGenerating = false;  // True if mesh is being generated on background thread
    private volatile float[] pendingMeshData = null;  // Mesh data ready for GPU upload (set by background thread)
    
    // Reference to ChunkManager for checking adjacent chunks (for proper face culling)
    private ChunkManager chunkManager;
    
    // Reference to TextureAtlas for UV coordinates
    private static TextureAtlas textureAtlas;

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
     * Set the static TextureAtlas reference (called once at startup).
     */
    public static void setTextureAtlas(TextureAtlas atlas) {
        textureAtlas = atlas;
    }
    
    /**
     * Set the ChunkManager reference (for cross-chunk queries).
     * Called after chunk creation.
     */
    public void setChunkManager(ChunkManager manager) {
        this.chunkManager = manager;
    }
    
    /**
     * Check if trees have been generated for this chunk.
     */
    public boolean hasTreesGenerated() {
        return treesGenerated;
    }
    
    /**
     * Mark that trees have been generated for this chunk.
     */
    public void setTreesGenerated(boolean generated) {
        this.treesGenerated = generated;
    }
    
    /**
     * Calculate sky lighting for this chunk.
     * Called after terrain generation.
     * 
     * Minecraft's sky light algorithm:
     * 1. For each column (x, z), scan from top down
     * 2. All air blocks above terrain have sky light = 15
     * 3. When hitting first opaque block, stop propagation in that column
     * 4. Transparent blocks (leaves, glass) allow reduced light through
     */
    public void calculateSkyLight() {
        for (int x = 0; x < SIZE; x++) {
            for (int z = 0; z < SIZE; z++) {
                int currentLight = 15;  // Start with full sky light at top
                
                // Scan from top down
                for (int y = HEIGHT - 1; y >= 0; y--) {
                    Block block = getBlock(x, y, z);
                    
                    // Set sky light for this block
                    block.setSkyLight(currentLight);
                    
                    // If we hit an opaque block, stop light propagation
                    if (block.isSolid() && !block.getType().isTransparent()) {
                        currentLight = 0;  // No more sky light below solid blocks
                    }
                    // Transparent blocks (leaves, glass) reduce light by 1
                    else if (block.isSolid() && block.getType().isTransparent()) {
                        currentLight = Math.max(0, currentLight - 1);
                    }
                    // Air blocks don't reduce light
                }
            }
        }
        
        // Mark mesh as dirty to regenerate with new lighting
        meshDirty = true;
    }
    
    /**
     * Get texture index for a block face.
     * Some blocks (grass, oak_log) have different textures for top/side/bottom.
     */
    private int getTextureIndex(Block.Type blockType, boolean isTop, boolean isBottom) {
        return switch (blockType) {
            case GRASS -> {
                if (isTop) yield TextureAtlas.TEX_GRASS_TOP;
                if (isBottom) yield TextureAtlas.TEX_DIRT;
                yield TextureAtlas.TEX_GRASS_SIDE;
            }
            case DIRT -> TextureAtlas.TEX_DIRT;
            case STONE -> TextureAtlas.TEX_STONE;
            case OAK_LOG -> {
                if (isTop || isBottom) yield TextureAtlas.TEX_OAK_LOG_TOP;
                yield TextureAtlas.TEX_OAK_LOG_SIDE;
            }
            case OAK_LEAVES -> TextureAtlas.TEX_OAK_LEAVES;
            case SAND -> TextureAtlas.TEX_SAND;
            default -> TextureAtlas.TEX_STONE;  // Fallback
        };
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
     * Check if a block is solid at LOCAL coordinates.
     * For edge blocks, checks adjacent chunks through ChunkManager.
     * This fixes chunk boundary seams!
     */
    private boolean isSolidAtLocal(int localX, int localY, int localZ) {
        // Check if within this chunk
        if (localX >= 0 && localX < SIZE && localZ >= 0 && localZ < SIZE) {
            if (localY < 0 || localY >= HEIGHT) {
                return false;  // Out of world bounds
            }
            return getBlock(localX, localY, localZ).isSolid();
        }
        
        // Block is outside this chunk - check adjacent chunk via ChunkManager
        if (chunkManager != null) {
            // Convert to world coordinates
            int worldX = chunkX * SIZE + localX;
            int worldZ = chunkZ * SIZE + localZ;
            return chunkManager.isBlockSolidAt(worldX, localY, worldZ);
        }
        
        // No ChunkManager reference - assume air (shouldn't happen)
        return false;
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
    /**
     * Generate mesh data ON BACKGROUND THREAD (Minecraft-style async).
     * Called by ChunkManager's executor service.
     */
    public void generateMeshAsync() {
        if (meshGenerating || !meshDirty) {
            return;  // Already generating or clean
        }
        
        meshGenerating = true;
        long startTime = System.nanoTime();
        
        meshData.clear();
        int blocksRendered = 0;

        // Minecraft optimization: Most blocks are AIR, skip them quickly
        for (int x = 0; x < SIZE; x++) {
            for (int y = 0; y < HEIGHT; y++) {
                for (int z = 0; z < SIZE; z++) {
                    Block block = blocks[x][y][z];
                    if (block.isSolid()) {
                        blocksRendered++;
                        addCubeMesh(x, y, z, block.getType());
                    }
                }
            }
        }

        meshDirty = false;  // Mark as clean
        
        // Convert to array and store as pending (for GPU upload on main thread)
        float[] meshArray = convertMeshToArray();
        pendingMeshData = meshArray;  // Set for main thread to upload
        vboNeedsUpdate = true;  // Signal GPU upload needed
        
        long meshTime = (System.nanoTime() - startTime) / 1_000_000;
        if (meshTime > 10) {
            System.out.println("⏱ ASYNC MESH (" + chunkX + "," + chunkZ + "): " +
                meshTime + "ms, blocks=" + blocksRendered + ", verts=" + meshData.size() + " [BACKGROUND]");
        }
        
        meshGenerating = false;  // Done
    }
    
    /**
     * Get pending mesh data (generated on background thread, ready for GPU upload).
     */
    public float[] getPendingMeshData() {
        return pendingMeshData;
    }
    
    /**
     * Clear pending mesh data after GPU upload.
     */
    public void clearPendingMeshData() {
        pendingMeshData = null;
    }
    
    /**
     * Generate mesh synchronously (fallback/initial load).
     * This is used during initial chunk generation before async system kicks in.
     */
    public float[] generateMesh() {
        if (!meshDirty && !meshData.isEmpty()) {
            return convertMeshToArray();
        }
        
        // Generate synchronously for initial load
        meshData.clear();
        int blocksRendered = 0;

        for (int x = 0; x < SIZE; x++) {
            for (int y = 0; y < HEIGHT; y++) {
                for (int z = 0; z < SIZE; z++) {
                    Block block = blocks[x][y][z];
                    if (block.isSolid()) {
                        blocksRendered++;
                        addCubeMesh(x, y, z, block.getType());
                    }
                }
            }
        }

        meshDirty = false;
        
        // IMPORTANT: Set pendingMeshData so Renderer uploads it to GPU!
        float[] meshArray = convertMeshToArray();
        pendingMeshData = meshArray;
        vboNeedsUpdate = true;
        
        
        return meshArray;
    }
    
    /**
     * Convert mesh data list to float array.
     * Each vertex has 8 floats: position (x,y,z) + color (r,g,b) + texCoord (u,v)
     */
    private float[] convertMeshToArray() {
        float[] vertices = new float[meshData.size() * 8];
        for (int i = 0; i < meshData.size(); i++) {
            float[] vertex = meshData.get(i);
            // Position
            vertices[i * 8 + 0] = vertex[0];  // x
            vertices[i * 8 + 1] = vertex[1];  // y
            vertices[i * 8 + 2] = vertex[2];  // z
            // Color (for lighting)
            vertices[i * 8 + 3] = vertex[3];  // r
            vertices[i * 8 + 4] = vertex[4];  // g
            vertices[i * 8 + 5] = vertex[5];  // b
            // Texture coordinates
            vertices[i * 8 + 6] = vertex[6];  // u
            vertices[i * 8 + 7] = vertex[7];  // v
        }
        return vertices;
    }

    /**
     * Add a cube mesh for a single block.
     * Only adds visible faces (face culling optimization).
     * Now properly checks adjacent chunks for edge blocks!
     * 
     * Minecraft-style transparency: Faces are visible if adjacent block is:
     * - AIR, OR
     * - Transparent (like leaves, glass)
     */
    private void addCubeMesh(int x, int y, int z, Block.Type type) {
        float size = 1.0f;

        // Check which faces are visible (Minecraft-style transparency culling)
        // A face is visible if the adjacent block is not solid OR is transparent
        boolean topVisible = !isOpaqueAtLocal(x, y + 1, z);
        boolean bottomVisible = !isOpaqueAtLocal(x, y - 1, z);
        boolean northVisible = !isOpaqueAtLocal(x, y, z + 1);
        boolean southVisible = !isOpaqueAtLocal(x, y, z - 1);
        boolean eastVisible = !isOpaqueAtLocal(x + 1, y, z);
        boolean westVisible = !isOpaqueAtLocal(x - 1, y, z);

        // Get color for each face type
        float[] topColor = type.getColor(true, false);
        float[] bottomColor = type.getColor(false, true);
        float[] sideColor = type.getColor(false, false);
        
        // Add vertices for visible faces only (proper cube faces with textures)
        if (topVisible) addTopFace(x, y, z, size, topColor, type);
        if (bottomVisible) addBottomFace(x, y, z, size, bottomColor, type);
        if (northVisible) addNorthFace(x, y, z, size, sideColor, type);
        if (southVisible) addSouthFace(x, y, z, size, sideColor, type);
        if (eastVisible) addEastFace(x, y, z, size, sideColor, type);
        if (westVisible) addWestFace(x, y, z, size, sideColor, type);
    }
    
    /**
     * Check if a block at local coordinates is opaque (blocks view).
     * Returns false for AIR and transparent blocks (leaves, glass).
     * This is used for face culling - we show faces adjacent to transparent blocks.
     */
    private boolean isOpaqueAtLocal(int localX, int localY, int localZ) {
        // Check if within this chunk
        if (localX >= 0 && localX < SIZE && localZ >= 0 && localZ < SIZE) {
            if (localY < 0 || localY >= HEIGHT) {
                return false;  // Out of world bounds = not opaque
            }
            Block block = getBlock(localX, localY, localZ);
            return block.isSolid() && !block.getType().isTransparent();
        }
        
        // Block is outside this chunk - check adjacent chunk via ChunkManager
        if (chunkManager != null) {
            // Convert to world coordinates
            int worldX = chunkX * SIZE + localX;
            int worldZ = chunkZ * SIZE + localZ;
            Block block = chunkManager.getBlockAt(worldX, localY, worldZ);
            if (block != null) {
                return block.isSolid() && !block.getType().isTransparent();
            }
        }
        
        // No block found - assume not opaque
        return false;
    }

    /**
     * Calculate ambient occlusion for a vertex.
     * Checks the 3 blocks that touch this corner and darkens based on how many are solid.
     * 
     * @param side1 Is the first adjacent block solid?
     * @param side2 Is the second adjacent block solid?
     * @param corner Is the diagonal corner block solid?
     * @return AO factor (0.0 = fully occluded/dark, 1.0 = no occlusion/bright)
     */
    private float calculateAO(boolean side1, boolean side2, boolean corner) {
        // Minecraft's AO formula
        // If both sides are solid, corner doesn't matter
        if (side1 && side2) {
            return 1.0f - AO_STRENGTH;  // Maximum darkening
        }
        // Count how many blocks are occluding this vertex
        int count = (side1 ? 1 : 0) + (side2 ? 1 : 0) + (corner ? 1 : 0);
        // Darken based on occlusion count (0-3 blocks)
        return 1.0f - (count / 3.0f) * AO_STRENGTH;
    }
    
    /**
     * Convert Minecraft light level (0-15) to brightness (0.0-1.0).
     * Uses Minecraft's exact formula from lightmap.fsh shader.
     * 
     * Formula: brightness = level / (4.0 - 3.0 * level)
     * This creates a non-linear curve where higher light levels have diminishing returns.
     */
    private float getLightBrightness(int lightLevel) {
        if (lightLevel == 0) return 0.0f;
        float level = lightLevel / 15.0f;  // Normalize to 0-1
        return level / (4.0f - 3.0f * level);
    }
    
    /**
     * Apply face brightness, AO, and sky light to a color using Minecraft's lighting formula.
     * 
     * This applies Minecraft's smooth lighting calculation:
     * 1. Convert light level (0-15) to base brightness with ambient minimum
     * 2. Apply face orientation darkening (top=1.0, bottom=0.5, sides=0.6-0.8)
     * 3. Apply ambient occlusion from adjacent blocks (darkens corners)
     * 
     * This ensures AO is visible even in dark areas, unlike the previous formula
     * where ambient light washed out AO in darkness.
     */
    private float[] applyLighting(float[] baseColor, float faceBrightness, float ao, int lightLevel) {
        // Convert light level (0-15) to brightness (0.0-1.0) using Minecraft's formula
        float lightBrightness = getLightBrightness(lightLevel);
        
        // Apply ambient light to the light level FIRST (ensures minimum brightness)
        // This gives us a base lighting value that ranges from 0.4 (dark) to ~0.93 (bright)
        float baseLighting = lightBrightness * MINECRAFT_LIGHT_POWER + MINECRAFT_AMBIENT_LIGHT;
        
        // NOW apply face brightness and AO as multipliers
        // This ensures AO darkens corners even in dark areas!
        float brightness = Math.min(1.0f, baseLighting * faceBrightness * ao);
        
        return new float[] {
            baseColor[0] * brightness,
            baseColor[1] * brightness,
            baseColor[2] * brightness
        };
    }
    
    /**
     * Check if a block at world coordinates is solid.
     * Used for AO calculations (may need to check adjacent chunks).
     */
    private boolean isSolidForAO(int localX, int localY, int localZ) {
        // For now, only check within this chunk
        // TODO: Check adjacent chunks for edge blocks
        if (localX < 0 || localX >= SIZE || localZ < 0 || localZ >= SIZE) {
            return false;  // Assume air outside chunk for now
        }
        if (localY < 0 || localY >= HEIGHT) {
            return false;  // Air above/below world
        }
        return getBlock(localX, localY, localZ).isSolid();
    }
    
    /**
     * Get light level at a position, with bounds checking.
     * Returns 15 (full bright) if out of bounds.
     * Used for smooth lighting - samples light from blocks surrounding each vertex.
     */
    private int getLightLevelAt(int localX, int localY, int localZ) {
        if (localX < 0 || localX >= SIZE || localZ < 0 || localZ >= SIZE) {
            return 15;  // Assume full light outside chunk
        }
        if (localY < 0 || localY >= HEIGHT) {
            return 15;  // Full light above/below world
        }
        return getBlock(localX, localY, localZ).getLightLevel();
    }
    
    /**
     * Calculate smooth light level for a vertex by averaging light from surrounding blocks.
     * This is Minecraft's smooth lighting algorithm - each vertex samples 4 blocks.
     * 
     * @param side1 First adjacent block position
     * @param side2 Second adjacent block position  
     * @param corner Corner block position
     * @param center Center block position (the face's block)
     * @return Average light level (0-15)
     */
    private int getSmoothLightLevel(int[] side1, int[] side2, int[] corner, int[] center) {
        int light1 = getLightLevelAt(side1[0], side1[1], side1[2]);
        int light2 = getLightLevelAt(side2[0], side2[1], side2[2]);
        int light3 = getLightLevelAt(corner[0], corner[1], corner[2]);
        int light4 = getLightLevelAt(center[0], center[1], center[2]);
        
        // Average the 4 light levels for smooth interpolation
        return (light1 + light2 + light3 + light4) / 4;
    }

    // Top face (Y+)
    private void addTopFace(float x, float y, float z, float size, float[] color, Block.Type blockType) {
        float x0 = x, x1 = x + size;
        float y1 = y + size;
        float z0 = z, z1 = z + size;
        
        int bx = (int)x, by = (int)y, bz = (int)z;
        
        // Get texture UVs for this face
        int texIndex = getTextureIndex(blockType, true, false);  // top face
        float[] uvs = textureAtlas.getUVs(texIndex);
        float uMin = uvs[0], vMin = uvs[1], uMax = uvs[2], vMax = uvs[3];
        
        // Calculate AO and SMOOTH LIGHT LEVEL for each vertex
        // Each vertex samples light from the 4 blocks that touch that corner!
        // Top face vertices check blocks above and around them
        
        // Front-left vertex (x0, y1, z1)
        boolean fl_north = isSolidForAO(bx, by + 1, bz + 1);
        boolean fl_west = isSolidForAO(bx - 1, by + 1, bz);
        boolean fl_corner = isSolidForAO(bx - 1, by + 1, bz + 1);
        float ao_fl = calculateAO(fl_north, fl_west, fl_corner);
        int light_fl = getSmoothLightLevel(
            new int[]{bx, by + 1, bz + 1},     // north
            new int[]{bx - 1, by + 1, bz},      // west
            new int[]{bx - 1, by + 1, bz + 1},  // corner
            new int[]{bx, by + 1, bz}           // center above
        );
        
        // Front-right vertex (x1, y1, z1)
        boolean fr_north = isSolidForAO(bx, by + 1, bz + 1);
        boolean fr_east = isSolidForAO(bx + 1, by + 1, bz);
        boolean fr_corner = isSolidForAO(bx + 1, by + 1, bz + 1);
        float ao_fr = calculateAO(fr_north, fr_east, fr_corner);
        int light_fr = getSmoothLightLevel(
            new int[]{bx, by + 1, bz + 1},     // north
            new int[]{bx + 1, by + 1, bz},      // east
            new int[]{bx + 1, by + 1, bz + 1},  // corner
            new int[]{bx, by + 1, bz}           // center above
        );
        
        // Back-right vertex (x1, y1, z0)
        boolean br_south = isSolidForAO(bx, by + 1, bz - 1);
        boolean br_east = isSolidForAO(bx + 1, by + 1, bz);
        boolean br_corner = isSolidForAO(bx + 1, by + 1, bz - 1);
        float ao_br = calculateAO(br_south, br_east, br_corner);
        int light_br = getSmoothLightLevel(
            new int[]{bx, by + 1, bz - 1},     // south
            new int[]{bx + 1, by + 1, bz},      // east
            new int[]{bx + 1, by + 1, bz - 1},  // corner
            new int[]{bx, by + 1, bz}           // center above
        );
        
        // Back-left vertex (x0, y1, z0)
        boolean bl_south = isSolidForAO(bx, by + 1, bz - 1);
        boolean bl_west = isSolidForAO(bx - 1, by + 1, bz);
        boolean bl_corner = isSolidForAO(bx - 1, by + 1, bz - 1);
        float ao_bl = calculateAO(bl_south, bl_west, bl_corner);
        int light_bl = getSmoothLightLevel(
            new int[]{bx, by + 1, bz - 1},     // south
            new int[]{bx - 1, by + 1, bz},      // west
            new int[]{bx - 1, by + 1, bz - 1},  // corner
            new int[]{bx, by + 1, bz}           // center above
        );
        
        // Apply lighting with PER-VERTEX light levels for smooth lighting!
        addQuad(
            x0, y1, z1, applyLighting(color, BRIGHTNESS_TOP, ao_fl, light_fl), uMin, vMax,  // Front-left
            x1, y1, z1, applyLighting(color, BRIGHTNESS_TOP, ao_fr, light_fr), uMax, vMax,  // Front-right
            x1, y1, z0, applyLighting(color, BRIGHTNESS_TOP, ao_br, light_br), uMax, vMin,  // Back-right
            x0, y1, z0, applyLighting(color, BRIGHTNESS_TOP, ao_bl, light_bl), uMin, vMin   // Back-left
        );
    }

    // Bottom face (Y-)
    private void addBottomFace(float x, float y, float z, float size, float[] color, Block.Type blockType) {
        float x0 = x, x1 = x + size;
        float y0 = y;
        float z0 = z, z1 = z + size;
        
        int bx = (int)x, by = (int)y, bz = (int)z;
        
        // Get texture UVs for this face (grass bottom = dirt texture)
        int texIndex = getTextureIndex(blockType, false, true);  // bottom face
        float[] uvs = textureAtlas.getUVs(texIndex);
        float uMin = uvs[0], vMin = uvs[1], uMax = uvs[2], vMax = uvs[3];
        
        // Bottom face vertices check blocks below - with SMOOTH LIGHTING!
        
        // Back-left vertex (x0, y0, z0)
        boolean bl_south = isSolidForAO(bx, by - 1, bz - 1);
        boolean bl_west = isSolidForAO(bx - 1, by - 1, bz);
        boolean bl_corner = isSolidForAO(bx - 1, by - 1, bz - 1);
        float ao_bl = calculateAO(bl_south, bl_west, bl_corner);
        int light_bl = getSmoothLightLevel(
            new int[]{bx, by - 1, bz - 1}, new int[]{bx - 1, by - 1, bz},
            new int[]{bx - 1, by - 1, bz - 1}, new int[]{bx, by - 1, bz}
        );
        
        // Back-right vertex (x1, y0, z0)
        boolean br_south = isSolidForAO(bx, by - 1, bz - 1);
        boolean br_east = isSolidForAO(bx + 1, by - 1, bz);
        boolean br_corner = isSolidForAO(bx + 1, by - 1, bz - 1);
        float ao_br = calculateAO(br_south, br_east, br_corner);
        int light_br = getSmoothLightLevel(
            new int[]{bx, by - 1, bz - 1}, new int[]{bx + 1, by - 1, bz},
            new int[]{bx + 1, by - 1, bz - 1}, new int[]{bx, by - 1, bz}
        );
        
        // Front-right vertex (x1, y0, z1)
        boolean fr_north = isSolidForAO(bx, by - 1, bz + 1);
        boolean fr_east = isSolidForAO(bx + 1, by - 1, bz);
        boolean fr_corner = isSolidForAO(bx + 1, by - 1, bz + 1);
        float ao_fr = calculateAO(fr_north, fr_east, fr_corner);
        int light_fr = getSmoothLightLevel(
            new int[]{bx, by - 1, bz + 1}, new int[]{bx + 1, by - 1, bz},
            new int[]{bx + 1, by - 1, bz + 1}, new int[]{bx, by - 1, bz}
        );
        
        // Front-left vertex (x0, y0, z1)
        boolean fl_north = isSolidForAO(bx, by - 1, bz + 1);
        boolean fl_west = isSolidForAO(bx - 1, by - 1, bz);
        boolean fl_corner = isSolidForAO(bx - 1, by - 1, bz + 1);
        float ao_fl = calculateAO(fl_north, fl_west, fl_corner);
        int light_fl = getSmoothLightLevel(
            new int[]{bx, by - 1, bz + 1}, new int[]{bx - 1, by - 1, bz},
            new int[]{bx - 1, by - 1, bz + 1}, new int[]{bx, by - 1, bz}
        );
        
        addQuad(
            x0, y0, z0, applyLighting(color, BRIGHTNESS_BOTTOM, ao_bl, light_bl), uMin, vMin,
            x1, y0, z0, applyLighting(color, BRIGHTNESS_BOTTOM, ao_br, light_br), uMax, vMin,
            x1, y0, z1, applyLighting(color, BRIGHTNESS_BOTTOM, ao_fr, light_fr), uMax, vMax,
            x0, y0, z1, applyLighting(color, BRIGHTNESS_BOTTOM, ao_fl, light_fl), uMin, vMax
        );
    }

    // North face (Z+)
    private void addNorthFace(float x, float y, float z, float size, float[] color, Block.Type blockType) {
        float x0 = x, x1 = x + size;
        float y0 = y, y1 = y + size;
        float z1 = z + size;
        
        int bx = (int)x, by = (int)y, bz = (int)z;
        
        // Get texture UVs for side face
        int texIndex = getTextureIndex(blockType, false, false);  // side face
        float[] uvs = textureAtlas.getUVs(texIndex);
        float uMin = uvs[0], vMin = uvs[1], uMax = uvs[2], vMax = uvs[3];
        
        // North face - vertices sample light from blocks TO THE NORTH (bz+1)
        // This ensures side faces stay bright even if there's a block on top!
        
        // Bottom-left vertex (x0, y0, z1)
        boolean bl_west = isSolidForAO(bx - 1, by, bz + 1);
        boolean bl_down = isSolidForAO(bx, by - 1, bz + 1);
        boolean bl_corner = isSolidForAO(bx - 1, by - 1, bz + 1);
        float ao_bl = calculateAO(bl_west, bl_down, bl_corner);
        int light_bl = getSmoothLightLevel(
            new int[]{bx - 1, by, bz + 1}, new int[]{bx, by - 1, bz + 1},
            new int[]{bx - 1, by - 1, bz + 1}, new int[]{bx, by, bz + 1}  // Sample from north!
        );
        
        // Bottom-right vertex (x1, y0, z1)
        boolean br_east = isSolidForAO(bx + 1, by, bz + 1);
        boolean br_down = isSolidForAO(bx, by - 1, bz + 1);
        boolean br_corner = isSolidForAO(bx + 1, by - 1, bz + 1);
        float ao_br = calculateAO(br_east, br_down, br_corner);
        int light_br = getSmoothLightLevel(
            new int[]{bx + 1, by, bz + 1}, new int[]{bx, by - 1, bz + 1},
            new int[]{bx + 1, by - 1, bz + 1}, new int[]{bx, by, bz + 1}
        );
        
        // Top-right vertex (x1, y1, z1)
        boolean tr_east = isSolidForAO(bx + 1, by, bz + 1);
        boolean tr_up = isSolidForAO(bx, by + 1, bz + 1);
        boolean tr_corner = isSolidForAO(bx + 1, by + 1, bz + 1);
        float ao_tr = calculateAO(tr_east, tr_up, tr_corner);
        int light_tr = getSmoothLightLevel(
            new int[]{bx + 1, by, bz + 1}, new int[]{bx, by + 1, bz + 1},
            new int[]{bx + 1, by + 1, bz + 1}, new int[]{bx, by, bz + 1}
        );
        
        // Top-left vertex (x0, y1, z1)
        boolean tl_west = isSolidForAO(bx - 1, by, bz + 1);
        boolean tl_up = isSolidForAO(bx, by + 1, bz + 1);
        boolean tl_corner = isSolidForAO(bx - 1, by + 1, bz + 1);
        float ao_tl = calculateAO(tl_west, tl_up, tl_corner);
        int light_tl = getSmoothLightLevel(
            new int[]{bx - 1, by, bz + 1}, new int[]{bx, by + 1, bz + 1},
            new int[]{bx - 1, by + 1, bz + 1}, new int[]{bx, by, bz + 1}
        );
        
        addQuad(
            x0, y0, z1, applyLighting(color, BRIGHTNESS_NORTH, ao_bl, light_bl), uMin, vMax,
            x1, y0, z1, applyLighting(color, BRIGHTNESS_NORTH, ao_br, light_br), uMax, vMax,
            x1, y1, z1, applyLighting(color, BRIGHTNESS_NORTH, ao_tr, light_tr), uMax, vMin,
            x0, y1, z1, applyLighting(color, BRIGHTNESS_NORTH, ao_tl, light_tl), uMin, vMin
        );
    }

    // South face (Z-)
    private void addSouthFace(float x, float y, float z, float size, float[] color, Block.Type blockType) {
        float x0 = x, x1 = x + size;
        float y0 = y, y1 = y + size;
        float z0 = z;
        
        int bx = (int)x, by = (int)y, bz = (int)z;
        
        // Get texture UVs for side face
        int texIndex = getTextureIndex(blockType, false, false);
        float[] uvs = textureAtlas.getUVs(texIndex);
        float uMin = uvs[0], vMin = uvs[1], uMax = uvs[2], vMax = uvs[3];
        
        // South face - sample from blocks TO THE SOUTH (bz-1)
        
        // Bottom-right vertex (x1, y0, z0)
        boolean br_east = isSolidForAO(bx + 1, by, bz - 1);
        boolean br_down = isSolidForAO(bx, by - 1, bz - 1);
        boolean br_corner = isSolidForAO(bx + 1, by - 1, bz - 1);
        float ao_br = calculateAO(br_east, br_down, br_corner);
        int light_br = getSmoothLightLevel(
            new int[]{bx + 1, by, bz - 1}, new int[]{bx, by - 1, bz - 1},
            new int[]{bx + 1, by - 1, bz - 1}, new int[]{bx, by, bz - 1}
        );
        
        // Bottom-left vertex (x0, y0, z0)
        boolean bl_west = isSolidForAO(bx - 1, by, bz - 1);
        boolean bl_down = isSolidForAO(bx, by - 1, bz - 1);
        boolean bl_corner = isSolidForAO(bx - 1, by - 1, bz - 1);
        float ao_bl = calculateAO(bl_west, bl_down, bl_corner);
        int light_bl = getSmoothLightLevel(
            new int[]{bx - 1, by, bz - 1}, new int[]{bx, by - 1, bz - 1},
            new int[]{bx - 1, by - 1, bz - 1}, new int[]{bx, by, bz - 1}
        );
        
        // Top-left vertex (x0, y1, z0)
        boolean tl_west = isSolidForAO(bx - 1, by, bz - 1);
        boolean tl_up = isSolidForAO(bx, by + 1, bz - 1);
        boolean tl_corner = isSolidForAO(bx - 1, by + 1, bz - 1);
        float ao_tl = calculateAO(tl_west, tl_up, tl_corner);
        int light_tl = getSmoothLightLevel(
            new int[]{bx - 1, by, bz - 1}, new int[]{bx, by + 1, bz - 1},
            new int[]{bx - 1, by + 1, bz - 1}, new int[]{bx, by, bz - 1}
        );
        
        // Top-right vertex (x1, y1, z0)
        boolean tr_east = isSolidForAO(bx + 1, by, bz - 1);
        boolean tr_up = isSolidForAO(bx, by + 1, bz - 1);
        boolean tr_corner = isSolidForAO(bx + 1, by + 1, bz - 1);
        float ao_tr = calculateAO(tr_east, tr_up, tr_corner);
        int light_tr = getSmoothLightLevel(
            new int[]{bx + 1, by, bz - 1}, new int[]{bx, by + 1, bz - 1},
            new int[]{bx + 1, by + 1, bz - 1}, new int[]{bx, by, bz - 1}
        );
        
        addQuad(
            x1, y0, z0, applyLighting(color, BRIGHTNESS_SOUTH, ao_br, light_br), uMin, vMax,
            x0, y0, z0, applyLighting(color, BRIGHTNESS_SOUTH, ao_bl, light_bl), uMax, vMax,
            x0, y1, z0, applyLighting(color, BRIGHTNESS_SOUTH, ao_tl, light_tl), uMax, vMin,
            x1, y1, z0, applyLighting(color, BRIGHTNESS_SOUTH, ao_tr, light_tr), uMin, vMin
        );
    }

    // East face (X+)
    private void addEastFace(float x, float y, float z, float size, float[] color, Block.Type blockType) {
        float x1 = x + size;
        float y0 = y, y1 = y + size;
        float z0 = z, z1 = z + size;
        
        int bx = (int)x, by = (int)y, bz = (int)z;
        
        // Get texture UVs for side face
        int texIndex = getTextureIndex(blockType, false, false);
        float[] uvs = textureAtlas.getUVs(texIndex);
        float uMin = uvs[0], vMin = uvs[1], uMax = uvs[2], vMax = uvs[3];
        
        // East face - sample from blocks TO THE EAST (bx+1)
        
        // Bottom-front vertex (x1, y0, z1)
        boolean bf_north = isSolidForAO(bx + 1, by, bz + 1);
        boolean bf_down = isSolidForAO(bx + 1, by - 1, bz);
        boolean bf_corner = isSolidForAO(bx + 1, by - 1, bz + 1);
        float ao_bf = calculateAO(bf_north, bf_down, bf_corner);
        int light_bf = getSmoothLightLevel(
            new int[]{bx + 1, by, bz + 1}, new int[]{bx + 1, by - 1, bz},
            new int[]{bx + 1, by - 1, bz + 1}, new int[]{bx + 1, by, bz}
        );
        
        // Bottom-back vertex (x1, y0, z0)
        boolean bb_south = isSolidForAO(bx + 1, by, bz - 1);
        boolean bb_down = isSolidForAO(bx + 1, by - 1, bz);
        boolean bb_corner = isSolidForAO(bx + 1, by - 1, bz - 1);
        float ao_bb = calculateAO(bb_south, bb_down, bb_corner);
        int light_bb = getSmoothLightLevel(
            new int[]{bx + 1, by, bz - 1}, new int[]{bx + 1, by - 1, bz},
            new int[]{bx + 1, by - 1, bz - 1}, new int[]{bx + 1, by, bz}
        );
        
        // Top-back vertex (x1, y1, z0)
        boolean tb_south = isSolidForAO(bx + 1, by, bz - 1);
        boolean tb_up = isSolidForAO(bx + 1, by + 1, bz);
        boolean tb_corner = isSolidForAO(bx + 1, by + 1, bz - 1);
        float ao_tb = calculateAO(tb_south, tb_up, tb_corner);
        int light_tb = getSmoothLightLevel(
            new int[]{bx + 1, by, bz - 1}, new int[]{bx + 1, by + 1, bz},
            new int[]{bx + 1, by + 1, bz - 1}, new int[]{bx + 1, by, bz}
        );
        
        // Top-front vertex (x1, y1, z1)
        boolean tf_north = isSolidForAO(bx + 1, by, bz + 1);
        boolean tf_up = isSolidForAO(bx + 1, by + 1, bz);
        boolean tf_corner = isSolidForAO(bx + 1, by + 1, bz + 1);
        float ao_tf = calculateAO(tf_north, tf_up, tf_corner);
        int light_tf = getSmoothLightLevel(
            new int[]{bx + 1, by, bz + 1}, new int[]{bx + 1, by + 1, bz},
            new int[]{bx + 1, by + 1, bz + 1}, new int[]{bx + 1, by, bz}
        );
        
        addQuad(
            x1, y0, z1, applyLighting(color, BRIGHTNESS_EAST, ao_bf, light_bf), uMin, vMax,
            x1, y0, z0, applyLighting(color, BRIGHTNESS_EAST, ao_bb, light_bb), uMax, vMax,
            x1, y1, z0, applyLighting(color, BRIGHTNESS_EAST, ao_tb, light_tb), uMax, vMin,
            x1, y1, z1, applyLighting(color, BRIGHTNESS_EAST, ao_tf, light_tf), uMin, vMin
        );
    }

    // West face (X-)
    private void addWestFace(float x, float y, float z, float size, float[] color, Block.Type blockType) {
        float x0 = x;
        float y0 = y, y1 = y + size;
        float z0 = z, z1 = z + size;
        
        int bx = (int)x, by = (int)y, bz = (int)z;
        
        // Get texture UVs for side face
        int texIndex = getTextureIndex(blockType, false, false);
        float[] uvs = textureAtlas.getUVs(texIndex);
        float uMin = uvs[0], vMin = uvs[1], uMax = uvs[2], vMax = uvs[3];
        
        // West face - sample from blocks TO THE WEST (bx-1)
        
        // Bottom-back vertex (x0, y0, z0)
        boolean bb_south = isSolidForAO(bx - 1, by, bz - 1);
        boolean bb_down = isSolidForAO(bx - 1, by - 1, bz);
        boolean bb_corner = isSolidForAO(bx - 1, by - 1, bz - 1);
        float ao_bb = calculateAO(bb_south, bb_down, bb_corner);
        int light_bb = getSmoothLightLevel(
            new int[]{bx - 1, by, bz - 1}, new int[]{bx - 1, by - 1, bz},
            new int[]{bx - 1, by - 1, bz - 1}, new int[]{bx - 1, by, bz}
        );
        
        // Bottom-front vertex (x0, y0, z1)
        boolean bf_north = isSolidForAO(bx - 1, by, bz + 1);
        boolean bf_down = isSolidForAO(bx - 1, by - 1, bz);
        boolean bf_corner = isSolidForAO(bx - 1, by - 1, bz + 1);
        float ao_bf = calculateAO(bf_north, bf_down, bf_corner);
        int light_bf = getSmoothLightLevel(
            new int[]{bx - 1, by, bz + 1}, new int[]{bx - 1, by - 1, bz},
            new int[]{bx - 1, by - 1, bz + 1}, new int[]{bx - 1, by, bz}
        );
        
        // Top-front vertex (x0, y1, z1)
        boolean tf_north = isSolidForAO(bx - 1, by, bz + 1);
        boolean tf_up = isSolidForAO(bx - 1, by + 1, bz);
        boolean tf_corner = isSolidForAO(bx - 1, by + 1, bz + 1);
        float ao_tf = calculateAO(tf_north, tf_up, tf_corner);
        int light_tf = getSmoothLightLevel(
            new int[]{bx - 1, by, bz + 1}, new int[]{bx - 1, by + 1, bz},
            new int[]{bx - 1, by + 1, bz + 1}, new int[]{bx - 1, by, bz}
        );
        
        // Top-back vertex (x0, y1, z0)
        boolean tb_south = isSolidForAO(bx - 1, by, bz - 1);
        boolean tb_up = isSolidForAO(bx - 1, by + 1, bz);
        boolean tb_corner = isSolidForAO(bx - 1, by + 1, bz - 1);
        float ao_tb = calculateAO(tb_south, tb_up, tb_corner);
        int light_tb = getSmoothLightLevel(
            new int[]{bx - 1, by, bz - 1}, new int[]{bx - 1, by + 1, bz},
            new int[]{bx - 1, by + 1, bz - 1}, new int[]{bx - 1, by, bz}
        );
        
        addQuad(
            x0, y0, z0, applyLighting(color, BRIGHTNESS_WEST, ao_bb, light_bb), uMin, vMax,
            x0, y0, z1, applyLighting(color, BRIGHTNESS_WEST, ao_bf, light_bf), uMax, vMax,
            x0, y1, z1, applyLighting(color, BRIGHTNESS_WEST, ao_tf, light_tf), uMax, vMin,
            x0, y1, z0, applyLighting(color, BRIGHTNESS_WEST, ao_tb, light_tb), uMin, vMin
        );
    }

    /**
     * Add a quad (4 vertices) as two triangles.
     * Vertices should be in counter-clockwise order when viewed from outside.
     */
    private void addQuad(float x1, float y1, float z1, float[] c1, float u1, float v1,
                        float x2, float y2, float z2, float[] c2, float u2, float v2,
                        float x3, float y3, float z3, float[] c3, float u3, float v3,
                        float x4, float y4, float z4, float[] c4, float u4, float v4) {
        // First triangle (v1, v2, v3)
        meshData.add(new float[]{x1, y1, z1, c1[0], c1[1], c1[2], u1, v1});
        meshData.add(new float[]{x2, y2, z2, c2[0], c2[1], c2[2], u2, v2});
        meshData.add(new float[]{x3, y3, z3, c3[0], c3[1], c3[2], u3, v3});

        // Second triangle (v1, v3, v4)
        meshData.add(new float[]{x1, y1, z1, c1[0], c1[1], c1[2], u1, v1});
        meshData.add(new float[]{x3, y3, z3, c3[0], c3[1], c3[2], u3, v3});
        meshData.add(new float[]{x4, y4, z4, c4[0], c4[1], c4[2], u4, v4});
    }
    
    /**
     * Mark mesh as dirty (needs regeneration).
     * Call this when blocks change.
     */
    public void markDirty() {
        this.meshDirty = true;
        this.vboNeedsUpdate = true;  // Minecraft-style: Mark VBO for reupload
    }
    
    /**
     * Check if mesh needs regeneration.
     */
    public boolean isDirty() {
        return meshDirty;
    }
    
    /**
     * Get the VBO ID for this chunk (Minecraft-style GPU caching).
     */
    public int getVboId() {
        return vboId;
    }
    
    /**
     * Set the VBO ID for this chunk.
     */
    public void setVboId(int vboId) {
        this.vboId = vboId;
    }
    
    /**
     * Get the vertex count for this chunk's mesh.
     */
    public int getVertexCount() {
        return vertexCount;
    }
    
    /**
     * Set the vertex count for this chunk's mesh.
     */
    public void setVertexCount(int vertexCount) {
        this.vertexCount = vertexCount;
    }
    
    /**
     * Check if VBO needs updating (Minecraft-style).
     */
    public boolean needsVboUpdate() {
        return vboNeedsUpdate;
    }
    
    /**
     * Mark VBO as up-to-date.
     */
    public void setVboUpdated() {
        this.vboNeedsUpdate = false;
    }
}


