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
    private boolean initialLoadComplete = false;  // Track if initial chunk loading is done
    
    // Minecraft-style: Throttle chunk loading to avoid lag spikes
    private static final int MAX_CHUNKS_PER_FRAME = 2;  // Only load 2 chunks per frame max
    
    // Minecraft-style: Async mesh generation (background thread)
    private final java.util.concurrent.ExecutorService meshGenerationExecutor;
    private final java.util.concurrent.ConcurrentLinkedQueue<Chunk> chunksNeedingMesh;

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
        
        // Minecraft-style: Create background thread pool for mesh generation
        // Minecraft uses 4 threads typically
        this.meshGenerationExecutor = java.util.concurrent.Executors.newFixedThreadPool(4,
            r -> {
                Thread t = new Thread(r, "Chunk-Mesh-Builder");
                t.setDaemon(true);  // Background thread
                return t;
            });
        this.chunksNeedingMesh = new java.util.concurrent.ConcurrentLinkedQueue<>();
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
        
        Chunk chunk = chunks.computeIfAbsent(key, k -> {
            Chunk newChunk = new Chunk(chunkX, chunkZ);
            newChunk.setChunkManager(this);  // Set reference for cross-chunk queries
            generateTerrain(newChunk);
            
            chunksGenerated++;
            chunksLoaded++;
            return newChunk;
        });
        
        // Generate trees AFTER chunk is in map (so placeBlock can find it)
        // Skip if initial load isn't complete (batch generation happens later)
        if (initialLoadComplete && chunk != null && !chunk.hasTreesGenerated()) {
            long startTotal = System.currentTimeMillis();
            
            long chunkSeed = (long) chunkX * 31 + (long) chunkZ * 17;
            java.util.Random random = new java.util.Random(chunkSeed + terrainGenerator.hashCode());
            
            long treeStart = System.currentTimeMillis();
            int treesGenerated = generateTreesForChunk(chunk, random);
            long treeTime = System.currentTimeMillis() - treeStart;
            
            chunk.setTreesGenerated(true);
            
            // Recalculate sky light AFTER trees
            long lightStart = System.currentTimeMillis();
            chunk.calculateSkyLight();
            long lightTime = System.currentTimeMillis() - lightStart;
            
            chunk.markDirty();
            
            // Queue mesh generation on background thread (Minecraft-style async!)
            queueMeshGeneration(chunk);
            
            long totalTime = System.currentTimeMillis() - startTotal;
            if (totalTime > 20) {  // Log slow chunks (>20ms = lag spike)
                System.out.println("⚠ SLOW CHUNK (" + chunkX + "," + chunkZ + "): Trees=" + treeTime + "ms, Light=" + lightTime + "ms, TOTAL=" + totalTime + "ms");
            }
        } else if (chunk != null && !chunk.hasTreesGenerated()) {
            // Initial load - calculate sky light (trees come later in batch)
            chunk.calculateSkyLight();
        }
        
        return chunk;
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
     * Also marks adjacent chunks dirty if block is at edge.
     */
    public void breakBlock(int worldX, int worldY, int worldZ) {
        ChunkPos pos = ChunkPos.fromWorldPos(worldX, worldZ);
        Chunk chunk = chunks.get(pos.toLong());
        
        if (chunk != null) {
            int localX = worldX & 15;
            int localZ = worldZ & 15;
            
            chunk.setBlock(localX, worldY, localZ, Block.Type.AIR);
            
            // Recalculate sky light for this column (light can now pass through!)
            recalculateSkyLightColumn(chunk, localX, localZ);
            
            chunk.markDirty();  // Force mesh regeneration
            
            // PERFORMANCE: Mark adjacent chunks dirty if block is at edge
            // This ensures proper face culling at chunk boundaries
            markAdjacentChunksDirty(localX, localZ, pos);
            
            System.out.println("Block broken at (" + worldX + ", " + worldY + ", " + worldZ + ")");
        }
    }
    
    /**
     * Place a block at world coordinates.
     * Sets the block to the specified type and marks the chunk as dirty.
     */
    public void placeBlock(int worldX, int worldY, int worldZ, Block.Type blockType) {
        placeBlock(worldX, worldY, worldZ, blockType, true);
    }
    
    /**
     * Place a block at world coordinates with optional lighting update.
     * @param updateLighting If false, skips sky light recalculation (for batch operations like tree generation)
     */
    public void placeBlock(int worldX, int worldY, int worldZ, Block.Type blockType, boolean updateLighting) {
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
            
            // Recalculate sky light for this column (critical for proper shadows!)
            if (updateLighting) {
                recalculateSkyLightColumn(chunk, localX, localZ);
                System.out.println("Block placed at (" + worldX + ", " + worldY + ", " + worldZ + "): " + blockType);
            }
            
            chunk.markDirty();  // Force mesh regeneration
            
            // PERFORMANCE: Mark adjacent chunks dirty if block is at edge
            if (updateLighting) {
                markAdjacentChunksDirty(localX, localZ, pos);
            }
        }
    }
    
    
    /**
     * Recalculate sky light for a single column when a block is placed or broken.
     * This ensures proper shadows appear immediately.
     */
    private void recalculateSkyLightColumn(Chunk chunk, int localX, int localZ) {
        int currentLight = 15;  // Start with full sky light at top
        
        // Scan from top down
        for (int y = Chunk.HEIGHT - 1; y >= 0; y--) {
            Block block = chunk.getBlock(localX, y, localZ);
            
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
    
    /**
     * Mark adjacent chunks as dirty if a block change occurs at a chunk edge.
     * This ensures proper face culling at chunk boundaries.
     */
    private void markAdjacentChunksDirty(int localX, int localZ, ChunkPos pos) {
        // Check if block is at chunk edge
        if (localX == 0) {
            // West edge - mark western neighbor dirty
            Chunk westChunk = chunks.get(new ChunkPos(pos.x() - 1, pos.z()).toLong());
            if (westChunk != null) westChunk.markDirty();
        } else if (localX == 15) {
            // East edge - mark eastern neighbor dirty
            Chunk eastChunk = chunks.get(new ChunkPos(pos.x() + 1, pos.z()).toLong());
            if (eastChunk != null) eastChunk.markDirty();
        }
        
        if (localZ == 0) {
            // South edge - mark southern neighbor dirty
            Chunk southChunk = chunks.get(new ChunkPos(pos.x(), pos.z() - 1).toLong());
            if (southChunk != null) southChunk.markDirty();
        } else if (localZ == 15) {
            // North edge - mark northern neighbor dirty
            Chunk northChunk = chunks.get(new ChunkPos(pos.x(), pos.z() + 1).toLong());
            if (northChunk != null) northChunk.markDirty();
        }
    }

    /**
     * Update loaded chunks based on player position.
     * Loads new chunks in range, unloads far chunks.
     * Minecraft-style: Throttle loading to prevent lag spikes.
     */
    public void updateLoadedChunks(float playerX, float playerZ) {
        ChunkPos playerChunk = ChunkPos.fromWorldPos(playerX, playerZ);
        
        // Minecraft-style throttling: Only load a few chunks per frame
        int chunksLoadedThisFrame = 0;
        
        // Load chunks in render distance (prioritize closest first)
        for (int radius = 0; radius <= renderDistance && chunksLoadedThisFrame < MAX_CHUNKS_PER_FRAME; radius++) {
            for (int x = -radius; x <= radius && chunksLoadedThisFrame < MAX_CHUNKS_PER_FRAME; x++) {
                for (int z = -radius; z <= radius && chunksLoadedThisFrame < MAX_CHUNKS_PER_FRAME; z++) {
                    // Only load chunks on current radius ring
                    if (Math.abs(x) == radius || Math.abs(z) == radius) {
                        // Only load chunks within circular radius
                        if (x * x + z * z <= renderDistance * renderDistance) {
                            int chunkX = playerChunk.x() + x;
                            int chunkZ = playerChunk.z() + z;
                            long key = new ChunkPos(chunkX, chunkZ).toLong();
                            
                            // Only count as "loaded this frame" if it's actually new
                            if (!chunks.containsKey(key)) {
                                getChunk(chunkX, chunkZ);
                                chunksLoadedThisFrame++;
                            }
                        }
                    }
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
        
        // NOTE: Trees are generated in a post-processing pass after all chunks are loaded
        // See: generateTreesForAllChunks()
    }
    
    /**
     * Generate trees for all loaded chunks (post-processing after terrain generation).
     * This must be called AFTER all initial chunks are generated so adjacent chunks exist.
     * Also marks initial load as complete so future chunks can generate trees individually.
     */
    public void generateTreesForAllChunks() {
        initialLoadComplete = true;  // Future chunks can now generate trees safely!
        System.out.println("Generating trees for " + chunks.size() + " chunks...");
        int totalTrees = 0;
        
        for (Chunk chunk : chunks.values()) {
            // Use chunk coordinates as seed for consistent tree placement
            long chunkSeed = (long) chunk.chunkX * 31 + (long) chunk.chunkZ * 17;
            java.util.Random random = new java.util.Random(chunkSeed + terrainGenerator.hashCode());
            
            int trees = generateTreesForChunk(chunk, random);
            totalTrees += trees;
        }
        
        System.out.println("✓ Generated " + totalTrees + " trees across " + chunks.size() + " chunks!");
        
        // NOW calculate sky light for all chunks (after trees are placed!)
        System.out.println("Calculating sky lighting for " + chunks.size() + " chunks...");
        for (Chunk chunk : chunks.values()) {
            chunk.calculateSkyLight();
            chunk.setTreesGenerated(true);  // Mark that trees have been generated
            chunk.markDirty();  // Mark as needing mesh regeneration
            
            // Generate mesh synchronously for initial chunks (needs to be ready immediately!)
            chunk.generateMesh();
        }
        System.out.println("✓ Sky lighting calculated!");
    }
    
    /**
     * Generate trees for a single chunk.
     * Returns the number of trees successfully generated.
     */
    private int generateTreesForChunk(Chunk chunk, java.util.Random random) {
        int chunkWorldX = chunk.chunkX * Chunk.SIZE;
        int chunkWorldZ = chunk.chunkZ * Chunk.SIZE;
        
        // Try to spawn trees at random positions within the chunk
        // 3-6 attempts per chunk (balanced for Minecraft-like density)
        int treeAttempts = 3 + random.nextInt(4);
        
        int treesGenerated = 0;
        int treesAttempted = 0;
        
        for (int i = 0; i < treeAttempts; i++) {
            // Random position within chunk (stay FAR from edges - oak trees need 3+ block radius!)
            // Oak leaves extend 2 blocks, so we need at least 3 blocks margin to stay inside chunk
            int localX = 3 + random.nextInt(Chunk.SIZE - 6);  // Keep 3 blocks from edges
            int localZ = 3 + random.nextInt(Chunk.SIZE - 6);
            int worldX = chunkWorldX + localX;
            int worldZ = chunkWorldZ + localZ;
            
            // Get surface height
            int surfaceY = terrainGenerator.getSurfaceHeight(worldX, worldZ);
            
            // Check biome - only spawn trees in forest and plains
            com.mineclone.world.Biome biome = terrainGenerator.getBiome(worldX, worldZ);
            boolean canHaveTrees = (biome == com.mineclone.world.Biome.FOREST || biome == com.mineclone.world.Biome.PLAINS);
            
            if (canHaveTrees) {
                // Realistic spawn chance: 30% for plains, 70% for forest
                float spawnChance = (biome == com.mineclone.world.Biome.FOREST) ? 0.70f : 0.30f;
                if (random.nextFloat() < spawnChance) {
                    treesAttempted++;
                    // Try to generate tree
                    boolean success = com.mineclone.world.TreeGenerator.generateOakTree(this, worldX, surfaceY + 1, worldZ, random);
                    if (success) {
                        treesGenerated++;
                    }
                }
            }
        }
        
        // Debug output for dynamically loaded chunks  
        if (treesAttempted > 0) {
            System.out.println("  Tree attempts in chunk (" + chunk.chunkX + "," + chunk.chunkZ + "): Attempted=" + treesAttempted + ", Success=" + treesGenerated);
        } else {
            System.out.println("  No tree attempts in chunk (" + chunk.chunkX + "," + chunk.chunkZ + ") - wrong biome?");
        }
        
        return treesGenerated;
    }
    
    /**
     * Generate trees in a chunk (forest and plains biomes).
     */
    private void generateTrees(Chunk chunk, java.util.Random random) {
        int chunkWorldX = chunk.chunkX * Chunk.SIZE;
        int chunkWorldZ = chunk.chunkZ * Chunk.SIZE;
        
        // DEBUG: Check biome distribution in this chunk
        com.mineclone.world.Biome centerBiome = terrainGenerator.getBiome(chunkWorldX + 8, chunkWorldZ + 8);
        
        // Try to spawn trees at random positions within the chunk
        // INCREASED for testing: 6-10 attempts per chunk
        int treeAttempts = 6 + random.nextInt(5);
        
        int treesGenerated = 0;
        for (int i = 0; i < treeAttempts; i++) {
            // Random position within chunk
            int localX = random.nextInt(Chunk.SIZE);
            int localZ = random.nextInt(Chunk.SIZE);
            int worldX = chunkWorldX + localX;
            int worldZ = chunkWorldZ + localZ;
            
            // Get surface height
            int surfaceY = terrainGenerator.getSurfaceHeight(worldX, worldZ);
            
            // Check biome - only spawn trees in forest and plains
            com.mineclone.world.Biome biome = terrainGenerator.getBiome(worldX, worldZ);
            boolean canHaveTrees = (biome == com.mineclone.world.Biome.FOREST || biome == com.mineclone.world.Biome.PLAINS);
            
            if (canHaveTrees) {
                // INCREASED spawn chance for testing: 80% for plains, 95% for forest
                float spawnChance = (biome == com.mineclone.world.Biome.FOREST) ? 0.95f : 0.80f;
                if (random.nextFloat() < spawnChance) {
                    // Try to generate tree
                    boolean success = com.mineclone.world.TreeGenerator.generateOakTree(this, worldX, surfaceY + 1, worldZ, random);
                    if (success) {
                        treesGenerated++;
                    }
                }
            }
        }
        
        // DEBUG output for first few chunks
        if (chunksGenerated < 5 || treesGenerated > 0) {
            System.out.println("Chunk [" + chunk.chunkX + "," + chunk.chunkZ + "] biome=" + centerBiome + ", trees=" + treesGenerated);
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
     * Queue a chunk for mesh generation on background thread (Minecraft-style).
     * This prevents lag spikes from mesh generation.
     */
    private void queueMeshGeneration(Chunk chunk) {
        meshGenerationExecutor.submit(() -> {
            try {
                // Generate mesh on background thread (CPU work happens here)
                chunk.generateMeshAsync();
                // GPU upload happens on main thread in Renderer
            } catch (Exception e) {
                System.err.println("⚠ Mesh gen error (" + chunk.chunkX + "," + chunk.chunkZ + "): " + e.getMessage());
                e.printStackTrace();
            }
        });
    }
    
    /**
     * Shutdown mesh generation threads (call on game exit).
     */
    public void shutdown() {
        meshGenerationExecutor.shutdown();
        try {
            if (!meshGenerationExecutor.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS)) {
                meshGenerationExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            meshGenerationExecutor.shutdownNow();
        }
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

