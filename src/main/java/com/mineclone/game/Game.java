package com.mineclone.game;

import com.mineclone.core.engine.Window;
import com.mineclone.core.input.MouseInput;
import com.mineclone.core.utils.Vector2f;
import org.joml.Matrix4f;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;

/**
 * Main game class for Minecraft clone
 */
public class Game implements com.mineclone.core.engine.IGameLogic {
    private Camera camera;
    private Player player;
    private ChunkManager chunkManager;
    private Renderer renderer;
    private SimpleCrosshairRenderer crosshairRenderer;
    private SimpleBlockOutlineRenderer blockOutlineRenderer;
    private long lastDebugTime;
    private boolean mouseLookEnabled;
    private RaycastResult currentLookingAt;  // Block player is currently looking at

    private static final float FOV = (float) Math.toRadians(70);
    private static final float Z_NEAR = 0.01f;
    private static final float Z_FAR = 1000.0f;
    private static final float MOUSE_SENSITIVITY = 0.1f;

    @Override
    public void init(Window window) throws Exception {
        System.out.println("=== Minecraft Clone - Improved Physics & Chunking ===");

        // Initialize OpenGL
        glEnable(GL_DEPTH_TEST);
        glEnable(GL_CULL_FACE);
        glCullFace(GL_BACK);
        glFrontFace(GL_CCW);

        // Create game objects
        camera = new Camera();
        player = new Player();
        crosshairRenderer = new SimpleCrosshairRenderer();
        blockOutlineRenderer = new SimpleBlockOutlineRenderer();
        
        // Create chunk manager with flat terrain at Y=64 (sea level)
        chunkManager = new ChunkManager(4, 64);  // 4 chunk render distance, flat at Y=64
        
        try {
            renderer = new Renderer();
        } catch (Exception e) {
            throw new RuntimeException("Failed to create renderer", e);
        }
        
        currentLookingAt = new RaycastResult();  // Initialize as no hit

        // Load initial chunks around spawn
        chunkManager.updateLoadedChunks(player.getPosition().x, player.getPosition().z);
        System.out.println("Loaded " + chunkManager.getLoadedChunkCount() + " chunks");
        
        // Debug: Check if terrain exists at spawn
        int spawnX = (int) player.getPosition().x;
        int spawnY = (int) player.getPosition().y;
        int spawnZ = (int) player.getPosition().z;
        System.out.println("Player spawn: (" + spawnX + ", " + spawnY + ", " + spawnZ + ")");
        System.out.println("Block below player: " + chunkManager.isBlockSolidAt(spawnX, spawnY - 1, spawnZ));
        System.out.println("Block at Y=64: " + chunkManager.isBlockSolidAt(spawnX, 64, spawnZ));

        System.out.println("=== Initialization Complete ===");
        System.out.println("=== CONTROLS: WASD to move, SPACE to jump, F to toggle mouse look ===");
    }

    @Override
    public void input(Window window, MouseInput mouseInput) {
        // Movement input (WASD)
        float moveForward = 0;
        float moveRight = 0;

        if (window.isKeyPressed(GLFW_KEY_W)) {
            moveForward += 1;
            System.out.println("W key pressed!");
        }
        if (window.isKeyPressed(GLFW_KEY_S)) moveForward -= 1;
        if (window.isKeyPressed(GLFW_KEY_A)) moveRight -= 1;
        if (window.isKeyPressed(GLFW_KEY_D)) moveRight += 1;

        // Jump input (Space)
        if (window.isKeyPressed(GLFW_KEY_SPACE)) {
            player.jump();
            System.out.println("SPACE pressed - Jump!");
        }

        // Toggle mouse look (F key)
        if (window.isKeyPressed(GLFW_KEY_F)) {
            mouseLookEnabled = !mouseLookEnabled;
            if (mouseLookEnabled) {
                window.setCursorMode(GLFW_CURSOR_DISABLED);
                System.out.println(">>> Mouse look ENABLED - cursor hidden and captured");
            } else {
                window.setCursorMode(GLFW_CURSOR_NORMAL);
                System.out.println(">>> Mouse look DISABLED - cursor visible");
            }
        }

        // Apply movement input to player (player handles physics internally)
        if (moveForward != 0 || moveRight != 0) {
            player.move(moveForward, moveRight);
        }

        // Mouse look - when enabled
        if (mouseLookEnabled) {
            Vector2f displacement = mouseInput.getDisplVec();
            if (displacement.x != 0 || displacement.y != 0) {
                // Apply mouse movement to player rotation
                // Yaw (horizontal) and pitch (vertical) with sensitivity
                float yawChange = displacement.x * MOUSE_SENSITIVITY;
                float pitchChange = displacement.y * MOUSE_SENSITIVITY;  // Fixed: removed negative for proper mouse direction
                
                player.rotate(yawChange, pitchChange);
            }
            
            // Mouse button handling (left = break, right = place)
            // Use click events to avoid repeated breaking/placing
            if (mouseInput.wasLeftButtonClicked()) {
                System.out.println(">>> LEFT CLICK detected!");
                handleBlockBreaking();
            }
            if (mouseInput.wasRightButtonClicked()) {
                System.out.println(">>> RIGHT CLICK detected!");
                handleBlockPlacing();
            }
        }
    }

    @Override
    public void update(float interval, MouseInput mouseInput) {
        // Update loaded chunks based on player position
        chunkManager.updateLoadedChunks(player.getPosition().x, player.getPosition().z);
        
        // Update player physics at fixed timestep
        updatePlayerPhysics(interval);
        
        // Perform raycasting to find which block player is looking at
        updateRaycast();

        // Debug output every 2 seconds
        long now = System.currentTimeMillis();
        if (now - lastDebugTime > 2000) {
            String lookingAt = currentLookingAt.isHit() ? 
                "block at " + currentLookingAt.getBlockPos() : 
                "nothing";
            System.out.printf("Player: (%.1f, %.1f, %.1f) pitch=%.1f yaw=%.1f | Target: %s%n",
                player.getPosition().x, player.getPosition().y, player.getPosition().z,
                player.getRotation().x, player.getRotation().y,
                lookingAt);
            lastDebugTime = now;
        }
    }
    
    /**
     * Update raycasting to detect which block the player is looking at.
     */
    private void updateRaycast() {
        // Get camera position and forward direction
        org.joml.Vector3f cameraPos = camera.getPosition();
        org.joml.Vector3f forward = camera.getForward();
        
        // Perform raycast
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
     * Places a block adjacent to the one being looked at.
     */
    private void handleBlockPlacing() {
        if (!currentLookingAt.isHit()) {
            System.out.println(">>> Cannot place - not looking at a block!");
            return;
        }
        
        org.joml.Vector3i adjacentPos = currentLookingAt.getAdjacentPos();
        
        // Don't place a block where the player is standing
        org.joml.Vector3f playerPos = player.getPosition();
        int playerBlockX = (int) Math.floor(playerPos.x);
        int playerBlockY = (int) Math.floor(playerPos.y);
        int playerBlockZ = (int) Math.floor(playerPos.z);
        
        // Check if placing would overlap with player (feet or head)
        if (adjacentPos.x == playerBlockX && 
            adjacentPos.z == playerBlockZ &&
            (adjacentPos.y == playerBlockY || adjacentPos.y == playerBlockY + 1)) {
            System.out.println(">>> Cannot place - would be inside player!");
            return;
        }
        
        // Place a grass block (you could make this selectable later)
        chunkManager.placeBlock(adjacentPos.x, adjacentPos.y, adjacentPos.z, Block.Type.GRASS);
        System.out.println(">>> PLACED block at: (" + adjacentPos.x + ", " + adjacentPos.y + ", " + adjacentPos.z + ")");
    }
    
    /**
     * Update player physics with proper AABB collision detection.
     * Player dimensions: 0.6 blocks wide, 1.8 blocks tall (like Minecraft)
     */
    private void updatePlayerPhysics(float deltaTime) {
        // Store last frame state for interpolation
        player.updateLastState();
        
        // Apply gravity ONLY if not on ground (otherwise we'd constantly push into ground)
        if (!player.isOnGround()) {
            player.getVelocity().y -= 32.0f * deltaTime;
            
            // Terminal velocity cap
            if (player.getVelocity().y < -78.4f) {
                player.getVelocity().y = -78.4f;
            }
        }
        
        // Player bounding box (Minecraft dimensions)
        final float PLAYER_WIDTH = 0.6f;
        final float PLAYER_HEIGHT = 1.8f;
        final float PLAYER_HALF_WIDTH = PLAYER_WIDTH / 2.0f;
        
        org.joml.Vector3f pos = player.getPosition();
        org.joml.Vector3f vel = player.getVelocity();
        
        // Move X axis with collision
        pos.x += vel.x * deltaTime;
        if (checkPlayerCollision(pos.x, pos.y, pos.z, PLAYER_HALF_WIDTH, PLAYER_HEIGHT)) {
            // Collision on X axis - snap back
            pos.x -= vel.x * deltaTime;
            vel.x = 0;
        }
        
        // Move Z axis with collision
        pos.z += vel.z * deltaTime;
        if (checkPlayerCollision(pos.x, pos.y, pos.z, PLAYER_HALF_WIDTH, PLAYER_HEIGHT)) {
            // Collision on Z axis - snap back
            pos.z -= vel.z * deltaTime;
            vel.z = 0;
        }
        
        // Move Y axis with collision
        pos.y += vel.y * deltaTime;
        boolean verticalCollision = checkPlayerCollision(pos.x, pos.y, pos.z, PLAYER_HALF_WIDTH, PLAYER_HEIGHT);
        
        if (verticalCollision) {
            if (vel.y < 0) {
                // Hit ground - snap ABOVE the block we hit
                int groundBlockY = (int) Math.floor(pos.y);  // The block we're inside/touching
                pos.y = groundBlockY + 1.0f;  // Stand ON TOP of it (feet at +1)
                vel.y = 0;
                player.setOnGround(true);
            } else {
                // Hit ceiling
                pos.y -= vel.y * deltaTime;
                vel.y = 0;
            }
        } else {
            player.setOnGround(false);
        }
        
        // Apply friction (Minecraft uses 0.546 for normal blocks = 0.6 * 0.91)
        // Higher friction = more responsive movement (less sliding)
        float friction = player.isOnGround() ? 0.50f : 0.98f;
        vel.x *= friction;
        vel.z *= friction;
        
        // Stop tiny movements (prevent jitter)
        if (Math.abs(vel.x) < 0.003f) vel.x = 0;
        if (Math.abs(vel.z) < 0.003f) vel.z = 0;
    }
    
    /**
     * Check if player's AABB collides with any solid blocks.
     * Player feet position is at feetY, extends up to feetY + height
     */
    private boolean checkPlayerCollision(float centerX, float feetY, float centerZ, float halfWidth, float height) {
        // Calculate the range of blocks to check
        // Add small epsilon to ensure we check boundary blocks
        float epsilon = 0.001f;
        
        int minX = (int) Math.floor(centerX - halfWidth + epsilon);
        int maxX = (int) Math.floor(centerX + halfWidth - epsilon);
        int minY = (int) Math.floor(feetY + epsilon);  // Feet position
        int maxY = (int) Math.floor(feetY + height - epsilon);  // Head position
        int minZ = (int) Math.floor(centerZ - halfWidth + epsilon);
        int maxZ = (int) Math.floor(centerZ + halfWidth - epsilon);
        
        // Check all blocks player overlaps with
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    if (chunkManager.isBlockSolidAt(x, y, z)) {
                        return true;
                    }
                }
            }
        }
        
        return false;
    }

    @Override
    public void render(Window window) {
        // Handle window resize
        if (window.isResized()) {
            glViewport(0, 0, window.getWidth(), window.getHeight());
            window.setResized(false);
        }

        // Clear screen once (sky blue)
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
        glClearColor(0.5f, 0.8f, 1.0f, 1.0f);

        // Setup camera with interpolation for smooth rendering
        // TODO: Get actual partial tick from engine
        float partialTick = 1.0f; // For now, use 1.0 (no interpolation)
        camera.setup(player, partialTick);

        // Create projection matrix
        Matrix4f projectionMatrix = new Matrix4f().perspective(
            FOV,
            (float) window.getWidth() / window.getHeight(),
            Z_NEAR,
            Z_FAR
        );

        // Render all loaded chunks at their world positions
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