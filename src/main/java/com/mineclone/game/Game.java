package com.mineclone.game;

import com.mineclone.core.engine.Window;
import com.mineclone.core.input.MouseInput;
import com.mineclone.core.utils.Vector2f;
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
    private long lastDebugTime;
    private boolean mouseLookEnabled;
    private RaycastResult currentLookingAt;
    
    // Stats tracking
    private long tickCount = 0;
    private long lastStatsTime = System.currentTimeMillis();
    private int ticksSinceLastStats = 0;

    private static final float FOV = (float) Math.toRadians(70);
    private static final float Z_NEAR = 0.01f;
    private static final float Z_FAR = 1000.0f;
    private static final float MOUSE_SENSITIVITY = 0.1f;

    @Override
    public void init(Window window) throws Exception {
        this.window = window;
        System.out.println("=== Minecraft Clone - Entity-Based Movement System ===");

        // Initialize OpenGL
        glEnable(GL_DEPTH_TEST);
        glEnable(GL_CULL_FACE);
        glCullFace(GL_BACK);
        glFrontFace(GL_CCW);

        // Create chunk manager first (needed by player)
        long worldSeed = new java.util.Random().nextLong();
        System.out.println("World seed: " + worldSeed);
        chunkManager = new ChunkManager(4, worldSeed);  // 4 chunk render distance
        
        // Create player with world reference
        player = new LocalPlayer(chunkManager);
        
        // Create camera and rendering objects
        camera = new Camera();
        crosshairRenderer = new SimpleCrosshairRenderer();
        blockOutlineRenderer = new SimpleBlockOutlineRenderer();
        
        try {
            renderer = new Renderer();
        } catch (Exception e) {
            throw new RuntimeException("Failed to create renderer", e);
        }
        
        // Initialize texture atlas
        TextureAtlas textureAtlas = new TextureAtlas();
        textureAtlas.load();
        renderer.setTextureAtlas(textureAtlas);
        Chunk.setTextureAtlas(textureAtlas);
        
        currentLookingAt = new RaycastResult();

        // Load initial chunks around spawn
        chunkManager.updateLoadedChunks((float)player.getX(), (float)player.getZ());
        System.out.println("Loaded " + chunkManager.getLoadedChunkCount() + " chunks");
        
        // Generate trees
        chunkManager.generateTreesForAllChunks();
        
        // Spawn player at surface
        int spawnX = 8;
        int spawnZ = 8;
        int surfaceY = chunkManager.getSurfaceHeight(spawnX, spawnZ);
        player.setPos(spawnX, surfaceY + 1, spawnZ);  // +1 to spawn above ground
        
        System.out.println("Player spawn: (" + spawnX + ", " + (surfaceY + 1) + ", " + spawnZ + ")");
        System.out.println("=== Initialization Complete ===");
        System.out.println("=== CONTROLS ===");
        System.out.println("  WASD: Move");
        System.out.println("  SHIFT: Sprint");
        System.out.println("  SPACE: Jump (double-tap to toggle FLYING mode)");
        System.out.println("  F: Toggle mouse look");
        System.out.println("  Left Click: Break block");
        System.out.println("  Right Click: Place block");
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
                System.out.println(">>> Mouse look ENABLED");
            } else {
                window.setCursorMode(GLFW_CURSOR_NORMAL);
                System.out.println(">>> Mouse look DISABLED");
            }
        }

        // Mouse look
        if (mouseLookEnabled) {
            Vector2f displacement = mouseInput.getDisplVec();
            if (displacement.x != 0 || displacement.y != 0) {
                player.handleMouseLook(displacement.x, displacement.y, MOUSE_SENSITIVITY);
            }
            
            // Mouse button handling
            if (mouseInput.wasLeftButtonClicked()) {
                handleBlockBreaking();
            }
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
        tickCount++;
        ticksSinceLastStats++;
        
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
            
            System.out.printf("🎮 TPS: %.1f | Speed: %.2f b/s | Flying: %s | Sprint: %s | Ground: %s%n",
                actualTPS, speedBlocksPerSecond, 
                player.isFlying() ? "YES" : "NO",
                player.isSprinting() ? "YES" : "NO",
                player.isOnGround() ? "YES" : "NO");
            
            ticksSinceLastStats = 0;
            lastStatsTime = currentTime;
        }

        // Debug output every 2 seconds
        long now = System.currentTimeMillis();
        if (now - lastDebugTime > 2000) {
            String lookingAt = currentLookingAt.isHit() ? 
                "block at " + currentLookingAt.getBlockPos() : 
                "nothing";
            System.out.printf("📍 Position: (%.1f, %.1f, %.1f) | Pitch: %.1f° | Yaw: %.1f° | %s%n",
                player.getX(), player.getY(), player.getZ(),
                player.getXRot(), player.getYRot(),
                lookingAt);
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
     * Handle block breaking (left mouse button).
     */
    private void handleBlockBreaking() {
        if (!currentLookingAt.isHit()) {
            System.out.println(">>> Cannot break - not looking at a block!");
            return;
        }
        
        org.joml.Vector3i blockPos = currentLookingAt.getBlockPos();
        chunkManager.breakBlock(blockPos.x, blockPos.y, blockPos.z);
        System.out.println(">>> BROKE block at: (" + blockPos.x + ", " + blockPos.y + ", " + blockPos.z + ")");
    }
    
    /**
     * Handle block placing (right mouse button).
     */
    private void handleBlockPlacing() {
        if (!currentLookingAt.isHit()) {
            System.out.println(">>> Cannot place - not looking at a block!");
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
            System.out.println(">>> Cannot place - would be inside player!");
            return;
        }
        
        chunkManager.placeBlock(adjacentPos.x, adjacentPos.y, adjacentPos.z, Block.Type.GRASS);
        System.out.println(">>> PLACED block at: (" + adjacentPos.x + ", " + adjacentPos.y + ", " + adjacentPos.z + ")");
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

        // Setup camera with interpolation for smooth rendering
        camera.setup(player, partialTick);
        
        // Update raycast with interpolated camera position
        updateRaycast();

        // Create projection matrix
        Matrix4f projectionMatrix = new Matrix4f().perspective(
            FOV,
            (float) window.getWidth() / window.getHeight(),
            Z_NEAR,
            Z_FAR
        );

        // Render all loaded chunks
        for (Chunk chunk : chunkManager.getLoadedChunks()) {
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
        
        // Render crosshair (2D overlay)
        crosshairRenderer.render(window.getWidth(), window.getHeight());
    }

    @Override
    public void cleanup() {
        renderer.cleanup();
        System.out.println("=== Game Cleanup Complete ===");
    }
}