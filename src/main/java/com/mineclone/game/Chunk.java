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
    
    // Minecraft's EXACT AO values (from BlockBehaviour.java line 297)
    // Solid blocks return 0.2, air/transparent return 1.0, then average 4 samples
    private static final float AO_SOLID_BRIGHTNESS = 0.2f;    // Solid block = 20% brightness
    private static final float AO_CLEAR_BRIGHTNESS = 1.0f;    // Air/transparent = 100% brightness
    
    // Minecraft's lighting constants
    // The lightmap.fsh does complex color addition + mixing that results in ~15-20% minimum
    // We need higher minimum because we're not using a full lightmap texture with color addition
    private static final float MIN_VISIBILITY_AMOUNT = 0.15f;     // 15% mix with gray
    private static final float MIN_VISIBILITY_GRAY = 0.75f;       // 75% gray value
    // This gives ~20-25% minimum brightness even in complete darkness
    
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
    
    // Reference to BiomeColorMap for grass/foliage colors
    private static BiomeColorMap biomeColorMap;

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
            // CRITICAL: Preserve light values when changing block type!
            // Creating a new Block() resets skyLight to 0, breaking lighting updates
            Block oldBlock = blocks[x][y][z];
            int oldSkyLight = oldBlock != null ? oldBlock.getSkyLight() : 15;
            int oldBlockLight = oldBlock != null ? oldBlock.getBlockLight() : 0;
            
            blocks[x][y][z] = new Block(type);
            blocks[x][y][z].setSkyLight(oldSkyLight);
            blocks[x][y][z].setBlockLight(oldBlockLight);
            
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
     * Set the static BiomeColorMap reference (called once at startup).
     */
    public static void setBiomeColorMap(BiomeColorMap colorMap) {
        biomeColorMap = colorMap;
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
     * IMPORTANT: Uses LOCAL mesh list to avoid thread-safety issues!
     */
    public void generateMeshAsync() {
        if (meshGenerating || !meshDirty) {
            return;  // Already generating or clean
        }
        
        meshGenerating = true;
        long startTime = System.nanoTime();
        
        // CRITICAL FIX: Use LOCAL mesh data to avoid concurrent modification!
        // Don't touch shared meshData list from background thread
        List<float[]> localMeshData = new ArrayList<>();
        int blocksRendered = 0;

        // Minecraft optimization: Most blocks are AIR, skip them quickly
        for (int x = 0; x < SIZE; x++) {
            for (int y = 0; y < HEIGHT; y++) {
                for (int z = 0; z < SIZE; z++) {
                    Block block = blocks[x][y][z];
                    if (block.isSolid()) {
                        blocksRendered++;
                        addCubeMeshToList(localMeshData, x, y, z, block.getType());
                    }
                }
            }
        }

        meshDirty = false;  // Mark as clean
        
        // Convert LOCAL mesh to array and store as pending (for GPU upload on main thread)
        float[] meshArray = convertMeshListToArray(localMeshData);
        pendingMeshData = meshArray;  // Set for main thread to upload
        vboNeedsUpdate = true;  // Signal GPU upload needed
        
        long meshTime = (System.nanoTime() - startTime) / 1_000_000;
        if (meshTime > 10) {
            System.out.println("⏱ ASYNC MESH (" + chunkX + "," + chunkZ + "): " +
                meshTime + "ms, blocks=" + blocksRendered + ", verts=" + localMeshData.size() + " [BACKGROUND]");
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
     * Check if there's pending mesh data ready for upload.
     */
    public boolean hasPendingMeshData() {
        return pendingMeshData != null && pendingMeshData.length > 0;
    }
    
    /**
     * Generate mesh synchronously (fallback/initial load).
     * This is used during initial chunk generation before async system kicks in.
     * IMPORTANT: Uses LOCAL mesh list to avoid thread-safety issues!
     */
    public float[] generateMesh() {
        if (!meshDirty && !meshData.isEmpty()) {
            return convertMeshToArray();
        }
        
        // CRITICAL FIX: Generate synchronously with LOCAL list to avoid concurrent modification!
        List<float[]> localMeshData = new ArrayList<>();
        int blocksRendered = 0;

        for (int x = 0; x < SIZE; x++) {
            for (int y = 0; y < HEIGHT; y++) {
                for (int z = 0; z < SIZE; z++) {
                    Block block = blocks[x][y][z];
                    if (block.isSolid()) {
                        blocksRendered++;
                        addCubeMeshToList(localMeshData, x, y, z, block.getType());
                    }
                }
            }
        }

        meshDirty = false;
        
        // Convert LOCAL mesh to array
        float[] meshArray = convertMeshListToArray(localMeshData);
        
        // IMPORTANT: Set pendingMeshData so Renderer uploads it to GPU!
        // Also update shared meshData for backward compat
        meshData.clear();
        meshData.addAll(localMeshData);
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
        int writeIndex = 0;
        for (int i = 0; i < meshData.size(); i++) {
            float[] vertex = meshData.get(i);
            if (vertex == null || vertex.length < 8) {
                System.err.println("WARNING: Invalid vertex at index " + i + " in chunk " + chunkX + "," + chunkZ);
                continue;  // Skip invalid vertices
            }
            // Position
            vertices[writeIndex * 8 + 0] = vertex[0];  // x
            vertices[writeIndex * 8 + 1] = vertex[1];  // y
            vertices[writeIndex * 8 + 2] = vertex[2];  // z
            // Color (for lighting)
            vertices[writeIndex * 8 + 3] = vertex[3];  // r
            vertices[writeIndex * 8 + 4] = vertex[4];  // g
            vertices[writeIndex * 8 + 5] = vertex[5];  // b
            // Texture coordinates
            vertices[writeIndex * 8 + 6] = vertex[6];  // u
            vertices[writeIndex * 8 + 7] = vertex[7];  // v
            writeIndex++;
        }
        
        // If we skipped any vertices, trim the array to actual size
        if (writeIndex < meshData.size()) {
            float[] trimmed = new float[writeIndex * 8];
            System.arraycopy(vertices, 0, trimmed, 0, writeIndex * 8);
            return trimmed;
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
    private static int grassBlockCounter = 0;  // Debug: Count all grass blocks
    
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
        
        // Debug: Log all grass blocks and their top visibility
        if (type == Block.Type.GRASS && grassBlockCounter < 10) {
            System.out.println(String.format("🌱 GRASS BLOCK #%d at (%d,%d,%d): topVisible=%b (block above=%s)",
                grassBlockCounter, x, y, z, topVisible, 
                isOpaqueAtLocal(x, y + 1, z) ? "OPAQUE" : "AIR/TRANSPARENT"));
            grassBlockCounter++;
        }

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
     * Minecraft's shouldRenderFace algorithm (Block.java:280-293).
     * Determines if a face should be rendered based on the neighbor block.
     * 
     * Returns TRUE if face should render, FALSE if culled.
     * 
     * Algorithm:
     * 1. If neighbor is AIR → render (true)
     * 2. If neighbor is transparent (leaves, glass) → render (true)
     * 3. If neighbor is opaque solid → cull (false)
     */
    private boolean shouldRenderFaceAt(int localX, int localY, int localZ) {
        // Check if within this chunk
        if (localX >= 0 && localX < SIZE && localZ >= 0 && localZ < SIZE) {
            if (localY < 0 || localY >= HEIGHT) {
                return true;  // Out of world bounds = render face (border of world)
            }
            Block block = getBlock(localX, localY, localZ);
            
            // AIR → always render
            if (block.getType() == Block.Type.AIR) {
                return true;
            }
            
            // Transparent block (leaves) → always render
            if (block.getType().isTransparent()) {
                return true;
            }
            
            // Opaque solid block → cull (don't render)
            return false;
        }
        
        // Block is outside this chunk - check adjacent chunk via ChunkManager
        if (chunkManager != null) {
            // Convert to world coordinates
            int worldX = chunkX * SIZE + localX;
            int worldZ = chunkZ * SIZE + localZ;
            Block block = chunkManager.getBlockAt(worldX, localY, worldZ);
            
            if (block == null) {
                return true;  // Chunk not loaded = render face
            }
            
            // AIR → always render
            if (block.getType() == Block.Type.AIR) {
                return true;
            }
            
            // Transparent block → always render
            if (block.getType().isTransparent()) {
                return true;
            }
            
            // Opaque solid → cull
            return false;
        }
        
        // No chunk manager = render face (safe default)
        return true;
    }
    
    /**
     * DEPRECATED: Use shouldRenderFaceAt() instead.
     * Kept for compatibility with old addCubeMesh() method.
     */
    private boolean isOpaqueAtLocal(int localX, int localY, int localZ) {
        return !shouldRenderFaceAt(localX, localY, localZ);
    }

    /**
     * Calculate ambient occlusion for a vertex using Minecraft's EXACT formula.
     * 
     * From BlockBehaviour.getShadeBrightness() and ModelBlockRenderer:
     *   - Solid blocks contribute 0.2 brightness
     *   - Air/transparent contribute 1.0 brightness
     *   - Average all 4 samples (3 neighbors + center) * 0.25
     * 
     * @param side1 Is the first adjacent block solid?
     * @param side2 Is the second adjacent block solid?
     * @param corner Is the diagonal corner block solid?
     * @return AO brightness (0.2-1.0, where 1.0 = fully bright, 0.2 = fully occluded)
     */
    private float calculateAO(boolean side1, boolean side2, boolean corner) {
        // Convert solid flags to brightness values
        float shade1 = side1 ? AO_SOLID_BRIGHTNESS : AO_CLEAR_BRIGHTNESS;
        float shade2 = side2 ? AO_SOLID_BRIGHTNESS : AO_CLEAR_BRIGHTNESS;
        float shade3 = corner ? AO_SOLID_BRIGHTNESS : AO_CLEAR_BRIGHTNESS;
        float shadeCenter = AO_CLEAR_BRIGHTNESS;  // Center (our current block) is always clear
        
        // Average all 4 samples (Minecraft's exact formula from ModelBlockRenderer line 448)
        float ao = (shade1 + shade2 + shade3 + shadeCenter) * 0.25f;
        
        // Results:
        // 0 solid: (1.0 + 1.0 + 1.0 + 1.0) * 0.25 = 1.0  (100% - no darkening)
        // 1 solid: (0.2 + 1.0 + 1.0 + 1.0) * 0.25 = 0.8  (80% - subtle)
        // 2 solid: (0.2 + 0.2 + 1.0 + 1.0) * 0.25 = 0.6  (60% - moderate)
        // 3 solid: (0.2 + 0.2 + 0.2 + 1.0) * 0.25 = 0.4  (40% - noticeable)
        
        return Math.max(0.2f, Math.min(1.0f, ao));
    }
    
    /**
     * Convert Minecraft light level (0-15) to brightness (0.0-1.0).
     * Uses Minecraft's exact formula from lightmap.fsh shader.
     * 
     * Formula: brightness = level / (4.0 - 3.0 * level)
     * This creates a non-linear curve where higher light levels have diminishing returns.
     */
    private float getLightBrightness(int lightLevel) {
        // Clamp input to valid range
        lightLevel = Math.max(0, Math.min(15, lightLevel));
        if (lightLevel == 0) return 0.0f;
        
        float level = lightLevel / 15.0f;  // Normalize to 0-1
        float denominator = 4.0f - 3.0f * level;
        
        // Prevent division by zero or negative denominator
        if (denominator <= 0.01f) {
            return 1.0f;  // Maximum brightness
        }
        
        float brightness = level / denominator;
        
        // Validate result
        if (Float.isNaN(brightness) || Float.isInfinite(brightness)) {
            return 0.5f;  // Safe fallback
        }
        
        return Math.max(0.0f, Math.min(1.0f, brightness));
    }
    
    /**
     * Apply face brightness, AO, and sky light using Minecraft's EXACT formula.
     * 
     * From lightmap.fsh and ModelBlockRenderer:
     *   1. Convert light level to curved brightness: v / (4.0 - 3.0 * v)
     *   2. Multiply by directional face shade (0.5-1.0)
     *   3. Multiply by AO (0.2-1.0 from averaging solid neighbors)
     *   4. Mix with 75% gray at 4% (applied TWICE in lightmap.fsh)
     * 
     * With Minecraft's AO (0.2-1.0) instead of our old (0.6-1.0), corners are much lighter!
     */
    private float[] applyLighting(float[] baseColor, float faceBrightness, float ao, int lightLevel) {
        // Validate inputs
        if (Float.isNaN(faceBrightness) || Float.isInfinite(faceBrightness)) faceBrightness = 1.0f;
        if (Float.isNaN(ao) || Float.isInfinite(ao)) ao = 1.0f;
        lightLevel = Math.max(0, Math.min(15, lightLevel));
        
        // Step 1: Curved brightness from light level
        float lightBrightness = getLightBrightness(lightLevel);
        
        // Step 2: Apply face brightness and AO
        float brightness = lightBrightness * faceBrightness * ao;
        
        // Step 3: Add minimum visibility (higher than Minecraft's lightmap because we're simpler)
        // This prevents areas from going pitch black and matches the feel of Minecraft's full lightmap
        brightness = brightness * (1.0f - MIN_VISIBILITY_AMOUNT) + MIN_VISIBILITY_GRAY * MIN_VISIBILITY_AMOUNT;
        
        // DEBUG: Log lighting values occasionally (reduced frequency)
        if (debugLightingCounter++ % 50000 == 0) {
            System.out.println(String.format("🔆 LIGHT: level=%d → brightness=%.3f (curved=%.3f × face=%.2f × ao=%.2f)", 
                lightLevel, brightness, lightBrightness, faceBrightness, ao));
        }
        
        // DEBUG: Log final brightness
        if (debugLightingCounter % 10000 == 0) {
            System.out.println(String.format("   → final=%.3f", brightness));
        }
        
        // Validate and clamp
        if (Float.isNaN(brightness) || Float.isInfinite(brightness)) brightness = 1.0f;
        brightness = Math.max(0.0f, Math.min(1.0f, brightness));
        
        return new float[] {
            Math.max(0.0f, Math.min(1.0f, baseColor[0] * brightness)),
            Math.max(0.0f, Math.min(1.0f, baseColor[1] * brightness)),
            Math.max(0.0f, Math.min(1.0f, baseColor[2] * brightness))
        };
    }
    
    private int debugLightingCounter = 0;
    
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
     * PROPERLY queries neighbor chunks at boundaries!
     * Used for smooth lighting - samples light from blocks surrounding each vertex.
     */
    private int getLightLevelAt(int localX, int localY, int localZ) {
        // Vertical bounds - outside world
        if (localY < 0) {
            return 0;  // Below world = dark
        }
        if (localY >= HEIGHT) {
            return 15;  // Above world = full skylight
        }
        
        // Within this chunk - fast path
        if (localX >= 0 && localX < SIZE && localZ >= 0 && localZ < SIZE) {
            return getBlock(localX, localY, localZ).getLightLevel();
        }
        
        // Outside this chunk - query neighbor chunk
        if (chunkManager != null) {
            // Convert to world coordinates
            int worldX = chunkX * SIZE + localX;
            int worldZ = chunkZ * SIZE + localZ;
            
            // Query block from chunk manager (handles chunk boundaries)
            Block neighborBlock = chunkManager.getBlockAt(worldX, localY, worldZ);
            if (neighborBlock != null) {
                return neighborBlock.getLightLevel();
            }
        }
        
        // Fallback: Neighbor chunk not loaded yet
        // Use the edge block's light value for smooth transitions
        // This prevents dark/bright edges at chunk boundaries
        int edgeX = Math.max(0, Math.min(SIZE - 1, localX));
        int edgeZ = Math.max(0, Math.min(SIZE - 1, localZ));
        Block edgeBlock = getBlock(edgeX, localY, edgeZ);
        
        return edgeBlock != null ? edgeBlock.getLightLevel() : 15;
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

    /**
     * Get rotated UV coordinates for 4 vertices (front-left, front-right, back-right, back-left).
     * Minecraft rotates grass block textures to break up visual repetition.
     * Returns [u0,v0, u1,v1, u2,v2, u3,v3] for the 4 corners after rotation.
     * 
     * Texture coordinate system: (0,0) = top-left, (1,1) = bottom-right
     */
    private float[] getRotatedUVs(float uMin, float vMin, float uMax, float vMax, int rotation) {
        // Define the 4 corners of the texture
        // Top-left, top-right, bottom-right, bottom-left in texture space
        float[][] corners = {
            {uMin, vMin},  // Top-left of texture
            {uMax, vMin},  // Top-right of texture  
            {uMax, vMax},  // Bottom-right of texture
            {uMin, vMax}   // Bottom-left of texture
        };
        
        // Rotation shifts which texture corner goes to which vertex
        // Vertices are: front-left, front-right, back-right, back-left
        int shift = rotation / 90;  // 0, 1, 2, or 3
        
        // Map rotated corners to vertices (clockwise shift)
        return new float[]{
            corners[(0 + shift) % 4][0], corners[(0 + shift) % 4][1],  // Front-left
            corners[(1 + shift) % 4][0], corners[(1 + shift) % 4][1],  // Front-right
            corners[(2 + shift) % 4][0], corners[(2 + shift) % 4][1],  // Back-right
            corners[(3 + shift) % 4][0], corners[(3 + shift) % 4][1]   // Back-left
        };
    }
    
    /**
     * Get rotation for a block at world position.
     * Minecraft uses block position to deterministically "randomize" rotation.
     */
    private static int grassRotationCounter = 0;  // Static counter for debugging
    
    private int getBlockRotation(int worldX, int worldY, int worldZ, Block.Type type) {
        // Only rotate grass blocks (Minecraft's approach)
        if (type != Block.Type.GRASS) {
            return 0;
        }
        
        // Use world position to get deterministic "random" rotation
        // Minecraft actually picks from 4 variants in blockstate JSON
        int hash = (worldX * 3129871) ^ (worldZ * 116129781) ^ (worldY);
        hash = hash * hash * 42317861 + hash * 11;
        
        // Map to 0, 90, 180, 270
        int variant = (hash >> 24) & 3;
        int rotation = variant * 90;
        
        // Grass rotation working! (Tested and verified with 0°, 90°, 180°, 270° rotations)
        
        return rotation;
    }
    
    private static int topFaceGrassCounter = 0;  // Debug counter for grass top faces
    
    // Top face (Y+)
    private void addTopFace(float x, float y, float z, float size, float[] color, Block.Type blockType) {
        float x0 = x, x1 = x + size;
        float y1 = y + size;
        float z0 = z, z1 = z + size;
        
        int bx = (int)x, by = (int)y, bz = (int)z;
        
        // Debug: Check if top faces are being added for grass
        if (blockType == Block.Type.GRASS && topFaceGrassCounter < 5) {
            System.out.println(String.format("✅ addTopFace called for GRASS at (%d,%d,%d)", bx, by, bz));
            topFaceGrassCounter++;
        }
        
        // Get texture UVs for this face
        int texIndex = getTextureIndex(blockType, true, false);  // top face
        float[] uvs = textureAtlas.getUVs(texIndex);
        
        // Apply rotation for grass blocks (Minecraft feature to break up tiling)
        int worldX = chunkX * SIZE + bx;
        int worldZ = chunkZ * SIZE + bz;
        int rotation = getBlockRotation(worldX, by, worldZ, blockType);
        float[] rotatedUVs = getRotatedUVs(uvs[0], uvs[1], uvs[2], uvs[3], rotation);
        
        // Extract rotated UVs for each vertex (FL, FR, BR, BL)
        float u_fl = rotatedUVs[0], v_fl = rotatedUVs[1];
        float u_fr = rotatedUVs[2], v_fr = rotatedUVs[3];
        float u_br = rotatedUVs[4], v_br = rotatedUVs[5];
        float u_bl = rotatedUVs[6], v_bl = rotatedUVs[7];
        
        // Debug: Log UV rotation (first few grass blocks)
        if (blockType == Block.Type.GRASS && grassRotationCounter <= 20) {
            System.out.println(String.format("  ↳ Original UVs: (%.3f,%.3f)-(%.3f,%.3f)", uvs[0], uvs[1], uvs[2], uvs[3]));
            System.out.println(String.format("  ↳ Rotated %d°: FL=(%.3f,%.3f) FR=(%.3f,%.3f) BR=(%.3f,%.3f) BL=(%.3f,%.3f)",
                rotation, u_fl, v_fl, u_fr, v_fr, u_br, v_br, u_bl, v_bl));
        }
        
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
        // Use rotated UVs for grass blocks (Minecraft's visual variety feature)
        addQuad(
            x0, y1, z1, applyLighting(color, BRIGHTNESS_TOP, ao_fl, light_fl), u_fl, v_fl,  // Front-left
            x1, y1, z1, applyLighting(color, BRIGHTNESS_TOP, ao_fr, light_fr), u_fr, v_fr,  // Front-right
            x1, y1, z0, applyLighting(color, BRIGHTNESS_TOP, ao_br, light_br), u_br, v_br,  // Back-right
            x0, y1, z0, applyLighting(color, BRIGHTNESS_TOP, ao_bl, light_bl), u_bl, v_bl   // Back-left
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
     * Check if mesh is currently being generated on background thread.
     */
    public boolean isMeshGenerating() {
        return meshGenerating;
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
    
    /**
     * THREAD-SAFE: Add cube mesh to a specific list (not shared meshData).
     * This allows multiple threads to generate meshes without interference.
     * NOW INCLUDES: Minecraft's full AO + smooth lighting system!
     */
    private void addCubeMeshToList(List<float[]> targetList, int x, int y, int z, Block.Type type) {
        float size = 1.0f;
        int bx = x, by = y, bz = z;
        
        // Get biome-based tinting (Minecraft colormap system)
        float[] baseTint = new float[]{1.0f, 1.0f, 1.0f};
        if (biomeColorMap != null) {
            if (type == Block.Type.GRASS) {
                int grassColor = biomeColorMap.getDefaultGrassColor();
                baseTint = BiomeColorMap.argbToRGB(grassColor);
            } else if (type == Block.Type.OAK_LEAVES) {
                int foliageColor = biomeColorMap.getDefaultFoliageColor();
                baseTint = BiomeColorMap.argbToRGB(foliageColor);
            }
        }
        
        // Get texture UVs for each face type
        int topTexIndex = getTextureIndex(type, true, false);
        float[] topUVs = textureAtlas.getUVs(topTexIndex);
        
        int bottomTexIndex = getTextureIndex(type, false, true);
        float[] bottomUVs = textureAtlas.getUVs(bottomTexIndex);
        
        int sideTexIndex = getTextureIndex(type, false, false);
        float[] sideUVs = textureAtlas.getUVs(sideTexIndex);
        
        // For grass blocks: only top gets tint, sides use base texture (no tint)
        float[] topBaseTint = baseTint;
        float[] sideBaseTint = (type == Block.Type.GRASS) ? new float[]{1.0f, 1.0f, 1.0f} : baseTint;
        
        // TOP FACE (+Y) with full AO and smooth lighting
        if (shouldRenderFaceAt(x, y + 1, z)) {
            // Calculate AO for each vertex
            boolean fl_north = isSolidForAO(bx, by + 1, bz + 1);
            boolean fl_west = isSolidForAO(bx - 1, by + 1, bz);
            boolean fl_corner = isSolidForAO(bx - 1, by + 1, bz + 1);
            float ao_fl = calculateAO(fl_north, fl_west, fl_corner);
            int light_fl = getSmoothLightLevel(
                new int[]{bx, by + 1, bz + 1}, new int[]{bx - 1, by + 1, bz},
                new int[]{bx - 1, by + 1, bz + 1}, new int[]{bx, by + 1, bz});
            
            boolean fr_north = isSolidForAO(bx, by + 1, bz + 1);
            boolean fr_east = isSolidForAO(bx + 1, by + 1, bz);
            boolean fr_corner = isSolidForAO(bx + 1, by + 1, bz + 1);
            float ao_fr = calculateAO(fr_north, fr_east, fr_corner);
            int light_fr = getSmoothLightLevel(
                new int[]{bx, by + 1, bz + 1}, new int[]{bx + 1, by + 1, bz},
                new int[]{bx + 1, by + 1, bz + 1}, new int[]{bx, by + 1, bz});
            
            boolean br_south = isSolidForAO(bx, by + 1, bz - 1);
            boolean br_east = isSolidForAO(bx + 1, by + 1, bz);
            boolean br_corner = isSolidForAO(bx + 1, by + 1, bz - 1);
            float ao_br = calculateAO(br_south, br_east, br_corner);
            int light_br = getSmoothLightLevel(
                new int[]{bx, by + 1, bz - 1}, new int[]{bx + 1, by + 1, bz},
                new int[]{bx + 1, by + 1, bz - 1}, new int[]{bx, by + 1, bz});
            
            boolean bl_south = isSolidForAO(bx, by + 1, bz - 1);
            boolean bl_west = isSolidForAO(bx - 1, by + 1, bz);
            boolean bl_corner = isSolidForAO(bx - 1, by + 1, bz - 1);
            float ao_bl = calculateAO(bl_south, bl_west, bl_corner);
            int light_bl = getSmoothLightLevel(
                new int[]{bx, by + 1, bz - 1}, new int[]{bx - 1, by + 1, bz},
                new int[]{bx - 1, by + 1, bz - 1}, new int[]{bx, by + 1, bz});
            
            // Apply grass block rotation (Minecraft feature for visual variety)
            int worldX = chunkX * SIZE + bx;
            int worldZ = chunkZ * SIZE + bz;
            int rotation = getBlockRotation(worldX, by, worldZ, type);
            float[] rotatedTopUVs = getRotatedUVs(topUVs[0], topUVs[1], topUVs[2], topUVs[3], rotation);
            
            // Grass rotation is working! (Logged first 20 blocks during testing)
            
            // Apply lighting with PER-VERTEX AO and light levels (Minecraft smooth lighting!)
            // Use rotated UVs for grass blocks
            addQuadToList(targetList,
                x, y + 1, z,     applyLighting(topBaseTint, BRIGHTNESS_TOP, ao_bl, light_bl), rotatedTopUVs[0], rotatedTopUVs[1],
                x, y + 1, z + 1, applyLighting(topBaseTint, BRIGHTNESS_TOP, ao_fl, light_fl), rotatedTopUVs[2], rotatedTopUVs[3],
                x + 1, y + 1, z + 1, applyLighting(topBaseTint, BRIGHTNESS_TOP, ao_fr, light_fr), rotatedTopUVs[4], rotatedTopUVs[5],
                x + 1, y + 1, z, applyLighting(topBaseTint, BRIGHTNESS_TOP, ao_br, light_br), rotatedTopUVs[6], rotatedTopUVs[7]);
        }
        
        // BOTTOM FACE (-Y) with AO and smooth lighting
        if (shouldRenderFaceAt(x, y - 1, z)) {
            boolean bl_south = isSolidForAO(bx, by - 1, bz - 1);
            boolean bl_west = isSolidForAO(bx - 1, by - 1, bz);
            boolean bl_corner = isSolidForAO(bx - 1, by - 1, bz - 1);
            float ao_bl = calculateAO(bl_south, bl_west, bl_corner);
            int light_bl = getSmoothLightLevel(
                new int[]{bx, by - 1, bz - 1}, new int[]{bx - 1, by - 1, bz},
                new int[]{bx - 1, by - 1, bz - 1}, new int[]{bx, by - 1, bz});
            
            boolean br_south = isSolidForAO(bx, by - 1, bz - 1);
            boolean br_east = isSolidForAO(bx + 1, by - 1, bz);
            boolean br_corner = isSolidForAO(bx + 1, by - 1, bz - 1);
            float ao_br = calculateAO(br_south, br_east, br_corner);
            int light_br = getSmoothLightLevel(
                new int[]{bx, by - 1, bz - 1}, new int[]{bx + 1, by - 1, bz},
                new int[]{bx + 1, by - 1, bz - 1}, new int[]{bx, by - 1, bz});
            
            boolean fr_north = isSolidForAO(bx, by - 1, bz + 1);
            boolean fr_east = isSolidForAO(bx + 1, by - 1, bz);
            boolean fr_corner = isSolidForAO(bx + 1, by - 1, bz + 1);
            float ao_fr = calculateAO(fr_north, fr_east, fr_corner);
            int light_fr = getSmoothLightLevel(
                new int[]{bx, by - 1, bz + 1}, new int[]{bx + 1, by - 1, bz},
                new int[]{bx + 1, by - 1, bz + 1}, new int[]{bx, by - 1, bz});
            
            boolean fl_north = isSolidForAO(bx, by - 1, bz + 1);
            boolean fl_west = isSolidForAO(bx - 1, by - 1, bz);
            boolean fl_corner = isSolidForAO(bx - 1, by - 1, bz + 1);
            float ao_fl = calculateAO(fl_north, fl_west, fl_corner);
            int light_fl = getSmoothLightLevel(
                new int[]{bx, by - 1, bz + 1}, new int[]{bx - 1, by - 1, bz},
                new int[]{bx - 1, by - 1, bz + 1}, new int[]{bx, by - 1, bz});
            
            addQuadToList(targetList,
                x, y, z,     applyLighting(topBaseTint, BRIGHTNESS_BOTTOM, ao_bl, light_bl), bottomUVs[0], bottomUVs[1],
                x + 1, y, z, applyLighting(topBaseTint, BRIGHTNESS_BOTTOM, ao_br, light_br), bottomUVs[2], bottomUVs[1],
                x + 1, y, z + 1, applyLighting(topBaseTint, BRIGHTNESS_BOTTOM, ao_fr, light_fr), bottomUVs[2], bottomUVs[3],
                x, y, z + 1, applyLighting(topBaseTint, BRIGHTNESS_BOTTOM, ao_fl, light_fl), bottomUVs[0], bottomUVs[3]);
        }
        
        // NORTH FACE (+Z) with AO and smooth lighting
        if (shouldRenderFaceAt(x, y, z + 1)) {
            boolean bl_west = isSolidForAO(bx - 1, by, bz + 1);
            boolean bl_down = isSolidForAO(bx, by - 1, bz + 1);
            boolean bl_corner = isSolidForAO(bx - 1, by - 1, bz + 1);
            float ao_bl = calculateAO(bl_west, bl_down, bl_corner);
            int light_bl = getSmoothLightLevel(
                new int[]{bx - 1, by, bz + 1}, new int[]{bx, by - 1, bz + 1},
                new int[]{bx - 1, by - 1, bz + 1}, new int[]{bx, by, bz + 1});
            
            boolean br_east = isSolidForAO(bx + 1, by, bz + 1);
            boolean br_down = isSolidForAO(bx, by - 1, bz + 1);
            boolean br_corner = isSolidForAO(bx + 1, by - 1, bz + 1);
            float ao_br = calculateAO(br_east, br_down, br_corner);
            int light_br = getSmoothLightLevel(
                new int[]{bx + 1, by, bz + 1}, new int[]{bx, by - 1, bz + 1},
                new int[]{bx + 1, by - 1, bz + 1}, new int[]{bx, by, bz + 1});
            
            boolean tr_east = isSolidForAO(bx + 1, by, bz + 1);
            boolean tr_up = isSolidForAO(bx, by + 1, bz + 1);
            boolean tr_corner = isSolidForAO(bx + 1, by + 1, bz + 1);
            float ao_tr = calculateAO(tr_east, tr_up, tr_corner);
            int light_tr = getSmoothLightLevel(
                new int[]{bx + 1, by, bz + 1}, new int[]{bx, by + 1, bz + 1},
                new int[]{bx + 1, by + 1, bz + 1}, new int[]{bx, by, bz + 1});
            
            boolean tl_west = isSolidForAO(bx - 1, by, bz + 1);
            boolean tl_up = isSolidForAO(bx, by + 1, bz + 1);
            boolean tl_corner = isSolidForAO(bx - 1, by + 1, bz + 1);
            float ao_tl = calculateAO(tl_west, tl_up, tl_corner);
            int light_tl = getSmoothLightLevel(
                new int[]{bx - 1, by, bz + 1}, new int[]{bx, by + 1, bz + 1},
                new int[]{bx - 1, by + 1, bz + 1}, new int[]{bx, by, bz + 1});
            
            addQuadToList(targetList,
                x, y, z + 1, applyLighting(sideBaseTint, BRIGHTNESS_NORTH, ao_bl, light_bl), sideUVs[0], sideUVs[3],
                x + 1, y, z + 1, applyLighting(sideBaseTint, BRIGHTNESS_NORTH, ao_br, light_br), sideUVs[2], sideUVs[3],
                x + 1, y + 1, z + 1, applyLighting(sideBaseTint, BRIGHTNESS_NORTH, ao_tr, light_tr), sideUVs[2], sideUVs[1],
                x, y + 1, z + 1, applyLighting(sideBaseTint, BRIGHTNESS_NORTH, ao_tl, light_tl), sideUVs[0], sideUVs[1]);
        }
        
        // SOUTH FACE (-Z) with AO and smooth lighting  
        if (shouldRenderFaceAt(x, y, z - 1)) {
            boolean br_east = isSolidForAO(bx + 1, by, bz - 1);
            boolean br_down = isSolidForAO(bx, by - 1, bz - 1);
            boolean br_corner = isSolidForAO(bx + 1, by - 1, bz - 1);
            float ao_br = calculateAO(br_east, br_down, br_corner);
            int light_br = getSmoothLightLevel(
                new int[]{bx + 1, by, bz - 1}, new int[]{bx, by - 1, bz - 1},
                new int[]{bx + 1, by - 1, bz - 1}, new int[]{bx, by, bz - 1});
            
            boolean bl_west = isSolidForAO(bx - 1, by, bz - 1);
            boolean bl_down = isSolidForAO(bx, by - 1, bz - 1);
            boolean bl_corner = isSolidForAO(bx - 1, by - 1, bz - 1);
            float ao_bl = calculateAO(bl_west, bl_down, bl_corner);
            int light_bl = getSmoothLightLevel(
                new int[]{bx - 1, by, bz - 1}, new int[]{bx, by - 1, bz - 1},
                new int[]{bx - 1, by - 1, bz - 1}, new int[]{bx, by, bz - 1});
            
            boolean tl_west = isSolidForAO(bx - 1, by, bz - 1);
            boolean tl_up = isSolidForAO(bx, by + 1, bz - 1);
            boolean tl_corner = isSolidForAO(bx - 1, by + 1, bz - 1);
            float ao_tl = calculateAO(tl_west, tl_up, tl_corner);
            int light_tl = getSmoothLightLevel(
                new int[]{bx - 1, by, bz - 1}, new int[]{bx, by + 1, bz - 1},
                new int[]{bx - 1, by + 1, bz - 1}, new int[]{bx, by, bz - 1});
            
            boolean tr_east = isSolidForAO(bx + 1, by, bz - 1);
            boolean tr_up = isSolidForAO(bx, by + 1, bz - 1);
            boolean tr_corner = isSolidForAO(bx + 1, by + 1, bz - 1);
            float ao_tr = calculateAO(tr_east, tr_up, tr_corner);
            int light_tr = getSmoothLightLevel(
                new int[]{bx + 1, by, bz - 1}, new int[]{bx, by + 1, bz - 1},
                new int[]{bx + 1, by + 1, bz - 1}, new int[]{bx, by, bz - 1});
            
            addQuadToList(targetList,
                x + 1, y, z, applyLighting(sideBaseTint, BRIGHTNESS_SOUTH, ao_br, light_br), sideUVs[2], sideUVs[3],
                x, y, z, applyLighting(sideBaseTint, BRIGHTNESS_SOUTH, ao_bl, light_bl), sideUVs[0], sideUVs[3],
                x, y + 1, z, applyLighting(sideBaseTint, BRIGHTNESS_SOUTH, ao_tl, light_tl), sideUVs[0], sideUVs[1],
                x + 1, y + 1, z, applyLighting(sideBaseTint, BRIGHTNESS_SOUTH, ao_tr, light_tr), sideUVs[2], sideUVs[1]);
        }
        
        // EAST FACE (+X) with AO and smooth lighting
        if (shouldRenderFaceAt(x + 1, y, z)) {
            boolean bf_north = isSolidForAO(bx + 1, by, bz + 1);
            boolean bf_down = isSolidForAO(bx + 1, by - 1, bz);
            boolean bf_corner = isSolidForAO(bx + 1, by - 1, bz + 1);
            float ao_bf = calculateAO(bf_north, bf_down, bf_corner);
            int light_bf = getSmoothLightLevel(
                new int[]{bx + 1, by, bz + 1}, new int[]{bx + 1, by - 1, bz},
                new int[]{bx + 1, by - 1, bz + 1}, new int[]{bx + 1, by, bz});
            
            boolean bb_south = isSolidForAO(bx + 1, by, bz - 1);
            boolean bb_down = isSolidForAO(bx + 1, by - 1, bz);
            boolean bb_corner = isSolidForAO(bx + 1, by - 1, bz - 1);
            float ao_bb = calculateAO(bb_south, bb_down, bb_corner);
            int light_bb = getSmoothLightLevel(
                new int[]{bx + 1, by, bz - 1}, new int[]{bx + 1, by - 1, bz},
                new int[]{bx + 1, by - 1, bz - 1}, new int[]{bx + 1, by, bz});
            
            boolean tb_south = isSolidForAO(bx + 1, by, bz - 1);
            boolean tb_up = isSolidForAO(bx + 1, by + 1, bz);
            boolean tb_corner = isSolidForAO(bx + 1, by + 1, bz - 1);
            float ao_tb = calculateAO(tb_south, tb_up, tb_corner);
            int light_tb = getSmoothLightLevel(
                new int[]{bx + 1, by, bz - 1}, new int[]{bx + 1, by + 1, bz},
                new int[]{bx + 1, by + 1, bz - 1}, new int[]{bx + 1, by, bz});
            
            boolean tf_north = isSolidForAO(bx + 1, by, bz + 1);
            boolean tf_up = isSolidForAO(bx + 1, by + 1, bz);
            boolean tf_corner = isSolidForAO(bx + 1, by + 1, bz + 1);
            float ao_tf = calculateAO(tf_north, tf_up, tf_corner);
            int light_tf = getSmoothLightLevel(
                new int[]{bx + 1, by, bz + 1}, new int[]{bx + 1, by + 1, bz},
                new int[]{bx + 1, by + 1, bz + 1}, new int[]{bx + 1, by, bz});
            
            addQuadToList(targetList,
                x + 1, y, z + 1, applyLighting(sideBaseTint, BRIGHTNESS_EAST, ao_bf, light_bf), sideUVs[0], sideUVs[3],
                x + 1, y, z, applyLighting(sideBaseTint, BRIGHTNESS_EAST, ao_bb, light_bb), sideUVs[2], sideUVs[3],
                x + 1, y + 1, z, applyLighting(sideBaseTint, BRIGHTNESS_EAST, ao_tb, light_tb), sideUVs[2], sideUVs[1],
                x + 1, y + 1, z + 1, applyLighting(sideBaseTint, BRIGHTNESS_EAST, ao_tf, light_tf), sideUVs[0], sideUVs[1]);
        }
        
        // WEST FACE (-X) with AO and smooth lighting
        if (shouldRenderFaceAt(x - 1, y, z)) {
            boolean bb_south = isSolidForAO(bx - 1, by, bz - 1);
            boolean bb_down = isSolidForAO(bx - 1, by - 1, bz);
            boolean bb_corner = isSolidForAO(bx - 1, by - 1, bz - 1);
            float ao_bb = calculateAO(bb_south, bb_down, bb_corner);
            int light_bb = getSmoothLightLevel(
                new int[]{bx - 1, by, bz - 1}, new int[]{bx - 1, by - 1, bz},
                new int[]{bx - 1, by - 1, bz - 1}, new int[]{bx - 1, by, bz});
            
            boolean bf_north = isSolidForAO(bx - 1, by, bz + 1);
            boolean bf_down = isSolidForAO(bx - 1, by - 1, bz);
            boolean bf_corner = isSolidForAO(bx - 1, by - 1, bz + 1);
            float ao_bf = calculateAO(bf_north, bf_down, bf_corner);
            int light_bf = getSmoothLightLevel(
                new int[]{bx - 1, by, bz + 1}, new int[]{bx - 1, by - 1, bz},
                new int[]{bx - 1, by - 1, bz + 1}, new int[]{bx - 1, by, bz});
            
            boolean tf_north = isSolidForAO(bx - 1, by, bz + 1);
            boolean tf_up = isSolidForAO(bx - 1, by + 1, bz);
            boolean tf_corner = isSolidForAO(bx - 1, by + 1, bz + 1);
            float ao_tf = calculateAO(tf_north, tf_up, tf_corner);
            int light_tf = getSmoothLightLevel(
                new int[]{bx - 1, by, bz + 1}, new int[]{bx - 1, by + 1, bz},
                new int[]{bx - 1, by + 1, bz + 1}, new int[]{bx - 1, by, bz});
            
            boolean tb_south = isSolidForAO(bx - 1, by, bz - 1);
            boolean tb_up = isSolidForAO(bx - 1, by + 1, bz);
            boolean tb_corner = isSolidForAO(bx - 1, by + 1, bz - 1);
            float ao_tb = calculateAO(tb_south, tb_up, tb_corner);
            int light_tb = getSmoothLightLevel(
                new int[]{bx - 1, by, bz - 1}, new int[]{bx - 1, by + 1, bz},
                new int[]{bx - 1, by + 1, bz - 1}, new int[]{bx - 1, by, bz});
            
            addQuadToList(targetList,
                x, y, z, applyLighting(sideBaseTint, BRIGHTNESS_WEST, ao_bb, light_bb), sideUVs[0], sideUVs[3],
                x, y, z + 1, applyLighting(sideBaseTint, BRIGHTNESS_WEST, ao_bf, light_bf), sideUVs[2], sideUVs[3],
                x, y + 1, z + 1, applyLighting(sideBaseTint, BRIGHTNESS_WEST, ao_tf, light_tf), sideUVs[2], sideUVs[1],
                x, y + 1, z, applyLighting(sideBaseTint, BRIGHTNESS_WEST, ao_tb, light_tb), sideUVs[0], sideUVs[1]);
        }
    }
    
    /**
     * THREAD-SAFE: Add a quad (2 triangles) to a specific list.
     */
    private void addQuadToList(List<float[]> targetList,
                        float x1, float y1, float z1, float[] c1, float u1, float v1,
                        float x2, float y2, float z2, float[] c2, float u2, float v2,
                        float x3, float y3, float z3, float[] c3, float u3, float v3,
                        float x4, float y4, float z4, float[] c4, float u4, float v4) {
        // First triangle (v1, v2, v3)
        targetList.add(new float[]{x1, y1, z1, c1[0], c1[1], c1[2], u1, v1});
        targetList.add(new float[]{x2, y2, z2, c2[0], c2[1], c2[2], u2, v2});
        targetList.add(new float[]{x3, y3, z3, c3[0], c3[1], c3[2], u3, v3});

        // Second triangle (v1, v3, v4)
        targetList.add(new float[]{x1, y1, z1, c1[0], c1[1], c1[2], u1, v1});
        targetList.add(new float[]{x3, y3, z3, c3[0], c3[1], c3[2], u3, v3});
        targetList.add(new float[]{x4, y4, z4, c4[0], c4[1], c4[2], u4, v4});
    }
    
    /**
     * THREAD-SAFE: Convert a specific mesh list to array (not shared meshData).
     */
    private float[] convertMeshListToArray(List<float[]> sourceList) {
        float[] vertices = new float[sourceList.size() * 8];
        int writeIndex = 0;
        for (int i = 0; i < sourceList.size(); i++) {
            float[] vertex = sourceList.get(i);
            if (vertex == null || vertex.length < 8) {
                System.err.println("WARNING: Invalid vertex at index " + i + " in chunk " + chunkX + "," + chunkZ);
                continue;  // Skip invalid vertices
            }
            // Position
            vertices[writeIndex * 8 + 0] = vertex[0];  // x
            vertices[writeIndex * 8 + 1] = vertex[1];  // y
            vertices[writeIndex * 8 + 2] = vertex[2];  // z
            // Color (for lighting)
            vertices[writeIndex * 8 + 3] = vertex[3];  // r
            vertices[writeIndex * 8 + 4] = vertex[4];  // g
            vertices[writeIndex * 8 + 5] = vertex[5];  // b
            // Texture coordinates
            vertices[writeIndex * 8 + 6] = vertex[6];  // u
            vertices[writeIndex * 8 + 7] = vertex[7];  // v
            writeIndex++;
        }
        
        // If we skipped any vertices, trim the array to actual size
        if (writeIndex < sourceList.size()) {
            float[] trimmed = new float[writeIndex * 8];
            System.arraycopy(vertices, 0, trimmed, 0, writeIndex * 8);
            return trimmed;
        }
        
        return vertices;
    }
}


