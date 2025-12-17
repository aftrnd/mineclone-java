package com.mineclone.game;

import com.mineclone.core.engine.Window;
import com.mineclone.core.input.MouseInput;
import com.mineclone.core.utils.Logger;
import com.mineclone.core.utils.RenderHealthCheck;
import com.mineclone.core.utils.Vector2f;
import com.mineclone.test.RenderPipelineTest;
import org.joml.Matrix4f;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;

/**
 * Main game class for Minecraft clone.
 * 
 * Architecture:
 * - LocalPlayer extends LivingEntity extends Entity
 * - Engine handles 20 TPS tick rate
 * - Game receives partialTick for smooth rendering
 * - Camera interpolates between ticks
 */
public class Game implements com.mineclone.core.engine.IGameLogic {
    private Window window;
    private Camera camera;
    private LocalPlayer player;
    private ChunkManager chunkManager;
    private Renderer renderer;
    private SimpleCrosshairRenderer crosshairRenderer;
    private SimpleBlockOutlineRenderer blockOutlineRenderer;
    private HandRenderer handRenderer;
    private BlockBreakingManager blockBreakingManager;
    private BlockBreakingRenderer blockBreakingRenderer;
    private long lastDebugTime;
    private boolean mouseLookEnabled;
    private boolean viewBobbingEnabled = true;
    private boolean isBreakingBlock = false;  // Track if left mouse is held
    private RaycastResult currentLookingAt;
    
    // Stats tracking
    private long tickCount = 0;
    private long lastStatsTime = System.currentTimeMillis();
    private int ticksSinceLastStats = 0;

    private static final float FOV = (float) Math.toRadians(70);  // Minecraft default FOV
    private static final float Z_NEAR = 0.01f;
    private static final float Z_FAR = 1000.0f;
    private static final float MOUSE_SENSITIVITY = 0.1f;

    @Override
    public void init(Window window) throws Exception {
        this.window = window;
        
        // Initialize logging system (set to INFO by default, DEBUG for development)
        Logger.setLevel(Logger.Level.INFO);
        Logger.info("Game", "=== Minecraft Clone - Entity-Based Movement System ===");
        
        // Run startup health checks
        RenderHealthCheck.performStartupCheck();
        
        // Run rendering pipeline tests
        if (!RenderPipelineTest.runAllTests()) {
            Logger.warn("Game", "Some rendering tests failed - continuing anyway");
        }
        
        // Run face culling tests (critical for correct rendering!)
        if (!com.mineclone.test.FaceCullingTest.runAllTests()) {
            Logger.error("Game", "Face culling tests FAILED - rendering may be broken!");
            throw new RuntimeException("Face culling tests failed! Check implementation.");
        }

        // Initialize OpenGL
        glEnable(GL_DEPTH_TEST);
        glEnable(GL_CULL_FACE);
        glCullFace(GL_BACK);
        glFrontFace(GL_CCW);
        
        RenderHealthCheck.checkGLError("OpenGL initialization");

        // Create chunk manager first (needed by player)
        long worldSeed = new java.util.Random().nextLong();
        Logger.info("World", "World seed: " + worldSeed);
        chunkManager = new ChunkManager(16, worldSeed);  // 16 chunk render distance (Minecraft default)
        
        // Create player with world reference
        player = new LocalPlayer(chunkManager);
        Logger.info("Game", "Player created");
        
        // Create camera and rendering objects
        camera = new Camera();
        crosshairRenderer = new SimpleCrosshairRenderer();
        blockOutlineRenderer = new SimpleBlockOutlineRenderer();
        handRenderer = new HandRenderer();
        blockBreakingManager = new BlockBreakingManager();
        blockBreakingRenderer = new BlockBreakingRenderer();
        Logger.info("Game", "Renderers created");
        
        try {
            renderer = new Renderer();
            handRenderer.init();
            blockBreakingRenderer.init();
            Logger.info("Game", "Renderer initialized successfully");
        } catch (Exception e) {
            Logger.error("Game", "Failed to create renderer", e);
            throw new RuntimeException("Failed to create renderer", e);
        }
        
        // Initialize texture atlas
        TextureAtlas textureAtlas = new TextureAtlas();
        textureAtlas.load();
        renderer.setTextureAtlas(textureAtlas);
        Chunk.setTextureAtlas(textureAtlas);
        
        // Verify texture atlas
        if (!RenderHealthCheck.validateTexture(textureAtlas.getTextureId(), "Block Atlas")) {
            throw new RuntimeException("Texture atlas validation failed!");
        }
        
        // Initialize biome colormap system (Minecraft's grass/foliage colors)
        BiomeColorMap biomeColorMap = new BiomeColorMap();
        biomeColorMap.load();
        Chunk.setBiomeColorMap(biomeColorMap);
        Logger.info("Game", "Biome colormap system initialized");
        
        currentLookingAt = new RaycastResult();

        // Spawn player at fixed position (chunks will load in first frame!)
        int spawnX = 8;
        int spawnZ = 8;
        player.setPos(spawnX, 65, spawnZ);  // Spawn high, will fall to ground
        
        // CRITICAL: Don't load ANY chunks during init - would block window!
        // Minecraft-style: Show window INSTANTLY, load chunks in first few frames
        chunkManager.setInitialLoadRadius(2);  // Start tiny (just 12 chunks)
        chunkManager.enableProgressiveLoading();  // Enable immediately
        
        Logger.info("World", "World ready - chunks will load as needed");
        Logger.info("Game", "Player spawn: (" + spawnX + ", 65, " + spawnZ + ") - falling to ground...");
        Logger.info("Game", "=== Initialization Complete ===");
        Logger.info("Controls", "=== CONTROLS ===");
        Logger.info("Controls", "  WASD: Move");
        Logger.info("Controls", "  SHIFT: Sprint");
        Logger.info("Controls", "  SPACE: Jump (double-tap to toggle FLYING mode)");
        Logger.info("Controls", "  F: Toggle mouse look");
        Logger.info("Controls", "  V: Toggle view bobbing");
        Logger.info("Controls", "  Left Click: Break block");
        Logger.info("Controls", "  Right Click: Place block");
    }

    @Override
    public void input(Window window, MouseInput mouseInput) {
        // Read keyboard state
        boolean forward = window.isKeyPressed(GLFW_KEY_W);
        boolean backward = window.isKeyPressed(GLFW_KEY_S);
        boolean left = window.isKeyPressed(GLFW_KEY_A);
        boolean right = window.isKeyPressed(GLFW_KEY_D);
        boolean jump = window.isKeyPressed(GLFW_KEY_SPACE);
        boolean sprint = window.isKeyPressed(GLFW_KEY_LEFT_SHIFT);
        boolean up = window.isKeyPressed(GLFW_KEY_SPACE);     // For flying
        boolean down = window.isKeyPressed(GLFW_KEY_LEFT_SHIFT); // For flying
        
        // Update player input
        player.setInput(forward, backward, left, right, jump, sprint, up, down);
        
        // Handle space press for flying toggle
        if (window.isKeyJustPressed(GLFW_KEY_SPACE)) {
            boolean wasDoubleTap = player.onSpacePress();
            if (wasDoubleTap) {
                System.out.println("✈️ Flying toggled!");
            }
        }
        
        // Toggle mouse look (F key)
        if (window.isKeyJustPressed(GLFW_KEY_F)) {
            mouseLookEnabled = !mouseLookEnabled;
            if (mouseLookEnabled) {
                window.setCursorMode(GLFW_CURSOR_DISABLED);
                Logger.info("Input", "Mouse look ENABLED");
            } else {
                window.setCursorMode(GLFW_CURSOR_NORMAL);
                Logger.info("Input", "Mouse look DISABLED");
            }
        }
        
        // Toggle view bobbing (V key)
        if (window.isKeyJustPressed(GLFW_KEY_V)) {
            viewBobbingEnabled = !viewBobbingEnabled;
            Logger.info("Input", "View bobbing " + (viewBobbingEnabled ? "ENABLED" : "DISABLED"));
        }
        
        // Toggle debug logging (L key)
        if (window.isKeyJustPressed(GLFW_KEY_L)) {
            if (Logger.isDebugEnabled()) {
                Logger.setLevel(Logger.Level.INFO);
            } else {
                Logger.setLevel(Logger.Level.DEBUG);
            }
        }

        // Mouse look
        if (mouseLookEnabled) {
            Vector2f displacement = mouseInput.getDisplVec();
            if (displacement.x != 0 || displacement.y != 0) {
                player.handleMouseLook(displacement.x, displacement.y, MOUSE_SENSITIVITY);
            }
            
            // Mouse button handling
            // Track left button state for progressive breaking (handled in update())
            isBreakingBlock = mouseInput.isLeftButtonPressed();
            
            // Right button CLICKED - place block
            if (mouseInput.wasRightButtonClicked()) {
                handleBlockPlacing();
            }
        }
        
        // Clear input flags
        window.clearJustPressed();
    }

    @Override
    public void update(float interval, MouseInput mouseInput) {
        // Tick player once per call (Engine handles 20 TPS timing)
        player.tick();
        handRenderer.tick();  // Update swing animation
        tickCount++;
        ticksSinceLastStats++;
        
        // Handle progressive block breaking (Minecraft-style)
        if (isBreakingBlock) {
            handleBlockBreaking(interval);
        } else {
            // Button released - cancel breaking
            if (blockBreakingManager.isBreakingAny()) {
                blockBreakingManager.reset();
            }
        }
        
        // Update loaded chunks
        chunkManager.updateLoadedChunks((float)player.getX(), (float)player.getZ());
        
        // Log stats every second
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastStatsTime >= 1000) {
            float elapsedSeconds = (currentTime - lastStatsTime) / 1000.0f;
            float actualTPS = ticksSinceLastStats / elapsedSeconds;
            
            // Calculate player speed (blocks/second)
            org.joml.Vector3d vel = player.getDeltaMovement();
            double horizontalSpeed = Math.sqrt(vel.x * vel.x + vel.z * vel.z);
            double speedBlocksPerSecond = horizontalSpeed * 20.0;  // velocity is per tick, 20 TPS
            
            Logger.debug("Stats", String.format("TPS: %.1f | Speed: %.2f b/s | Flying: %s | Sprint: %s | Ground: %s",
                actualTPS, speedBlocksPerSecond, 
                player.isFlying() ? "YES" : "NO",
                player.isSprinting() ? "YES" : "NO",
                player.isOnGround() ? "YES" : "NO"));
            
            ticksSinceLastStats = 0;
            lastStatsTime = currentTime;
        }

        // Debug output every 5 seconds
        long now = System.currentTimeMillis();
        if (now - lastDebugTime > 5000) {
            String lookingAt = currentLookingAt.isHit() ? 
                "block at " + currentLookingAt.getBlockPos() : 
                "nothing";
            Logger.debug("Player", String.format("Position: (%.1f, %.1f, %.1f) | Pitch: %.1f° | Yaw: %.1f° | %s",
                player.getX(), player.getY(), player.getZ(),
                player.getXRot(), player.getYRot(),
                lookingAt));
            lastDebugTime = now;
        }
    }
    
    /**
     * Update raycasting to detect which block the player is looking at.
     */
    private void updateRaycast() {
        org.joml.Vector3f cameraPos = camera.getPosition();
        org.joml.Vector3f forward = camera.getForward();
        currentLookingAt = ImprovedRaycaster.raycast(cameraPos, forward, chunkManager);
    }
    
    /**
     * Handle block breaking (left mouse button HELD).
     * Uses Minecraft-style progressive breaking based on block hardness.
     */
    private void handleBlockBreaking(float deltaTime) {
        if (!currentLookingAt.isHit()) {
            // Not looking at a block - cancel any breaking in progress
            if (blockBreakingManager.isBreakingAny()) {
                blockBreakingManager.reset();
            }
            return;
        }
        
        org.joml.Vector3i blockPos = currentLookingAt.getBlockPos();
        
        // Check if we're starting to break a new block
        if (!blockBreakingManager.isBreaking(blockPos.x, blockPos.y, blockPos.z)) {
            // Get block type for hardness
            Block block = chunkManager.getBlockAt(blockPos.x, blockPos.y, blockPos.z);
            if (block != null && block.getType() != Block.Type.AIR) {
                blockBreakingManager.startBreaking(blockPos.x, blockPos.y, blockPos.z, block.getType());
                // Trigger initial hand swing animation
                handRenderer.startSwing();
            }
        }
        
        // Minecraft continuously swings hand while breaking - trigger new swing every 6 ticks (0.3s)
        // This matches the SWING_DURATION in HandRenderer
        if (blockBreakingManager.isBreakingAny() && tickCount % 6 == 0) {
            handRenderer.startSwing();
        }
        
        // Update breaking progress
        if (blockBreakingManager.updateBreaking(deltaTime)) {
            // Block fully broken!
            chunkManager.breakBlock(blockPos.x, blockPos.y, blockPos.z);
            Logger.info("Block", "BROKE block at: (" + blockPos.x + ", " + blockPos.y + ", " + blockPos.z + ")");
        }
    }
    
    /**
     * Handle block placing (right mouse button).
     */
    private void handleBlockPlacing() {
        if (!currentLookingAt.isHit()) {
            Logger.debug("Block", "Cannot place - not looking at a block!");
            return;
        }
        
        org.joml.Vector3i adjacentPos = currentLookingAt.getAdjacentPos();
        
        // Don't place inside player
        int playerBlockX = (int) Math.floor(player.getX());
        int playerBlockY = (int) Math.floor(player.getY());
        int playerBlockZ = (int) Math.floor(player.getZ());
        
        if (adjacentPos.x == playerBlockX && 
            adjacentPos.z == playerBlockZ &&
            (adjacentPos.y == playerBlockY || adjacentPos.y == playerBlockY + 1)) {
            Logger.debug("Block", "Cannot place - would be inside player!");
            return;
        }
        
        chunkManager.placeBlock(adjacentPos.x, adjacentPos.y, adjacentPos.z, Block.Type.GRASS);
        
        // Trigger hand swing animation
        handRenderer.startSwing();
        
        Logger.info("Block", "PLACED block at: (" + adjacentPos.x + ", " + adjacentPos.y + ", " + adjacentPos.z + ")");
    }

    @Override
    public void render(Window window, float partialTick) {
        // Handle window resize
        if (window.isResized()) {
            glViewport(0, 0, window.getWidth(), window.getHeight());
            window.setResized(false);
        }

        // Clear screen (sky blue)
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
        glClearColor(0.5f, 0.8f, 1.0f, 1.0f);

        // Setup camera with interpolation and view bobbing
        camera.setup(player, partialTick, viewBobbingEnabled);
        
        // Update raycast with interpolated camera position
        updateRaycast();

        // Create projection matrix
        Matrix4f projectionMatrix = new Matrix4f().perspective(
            FOV,
            (float) window.getWidth() / window.getHeight(),
            Z_NEAR,
            Z_FAR
        );

        // Render all loaded chunks (queue dirty chunks for async rebuild - Minecraft-style!)
        for (Chunk chunk : chunkManager.getLoadedChunks()) {
            // Queue mesh generation on background thread if dirty (prevents freezing!)
            if (chunk.isDirty() && !chunk.isMeshGenerating()) {
                chunkManager.queueMeshGeneration(chunk);
            }
            
            // Renderer will handle mesh upload and rendering (includes async upload)
            renderer.render(camera, chunk, projectionMatrix);
        }
        
        // Render block outline if looking at a block
        if (currentLookingAt.isHit()) {
            blockOutlineRenderer.render(
                currentLookingAt.getBlockPos(),
                camera.getViewMatrix(),
                projectionMatrix
            );
        }
        
        // === RENDER BLOCK BREAKING OVERLAY ===
        if (blockBreakingManager.isBreakingAny()) {
            int[] targetBlock = blockBreakingManager.getTargetBlock();
            if (targetBlock != null) {
                int breakingStage = blockBreakingManager.getBreakingStage();
                blockBreakingRenderer.renderBreakingBlock(
                    targetBlock[0], targetBlock[1], targetBlock[2],
                    breakingStage,
                    projectionMatrix,
                    camera.getViewMatrix()
                );
            }
        }
        
        // === RENDER HAND (First-person overlay) ===
        // Clear depth buffer so hand renders on top of world
        glClear(GL_DEPTH_BUFFER_BIT);
        handRenderer.render(projectionMatrix, camera.getViewMatrix(), partialTick, player);
        
        // Render crosshair (2D overlay)
        crosshairRenderer.render(window.getWidth(), window.getHeight());
    }

    @Override
    public void cleanup() {
        Logger.info("Game", "Starting cleanup...");
        if (renderer != null) {
            renderer.cleanup();
        }
        if (handRenderer != null) {
            handRenderer.cleanup();
        }
        Logger.info("Game", "=== Game Cleanup Complete ===");
    }
}