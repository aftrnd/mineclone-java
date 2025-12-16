package com.mineclone.game;

import org.joml.Vector3f;

/**
 * Player entity with physics-based movement system.
 * Based on Minecraft's LocalPlayer implementation with proper delta-time physics.
 * 
 * Key improvements over naive implementation:
 * - Separate movement intent from physics velocity
 * - Proper ground detection via collision, not velocity
 * - Friction applied after movement, before next frame
 * - Delta-time based movement calculations
 * - AABB collision detection with world
 */
public class Player {
    // Position and movement state
    private Vector3f position;
    private Vector3f velocity;
    private Vector3f rotation;
    
    // Previous frame state for interpolation (Minecraft-style)
    private Vector3f lastPosition;
    private Vector3f lastRotation;
    
    // Physics state
    private boolean onGround;
    private boolean horizontalCollision;
    private boolean flying;
    
    // Flying state tracking for double-tap detection
    private long lastSpacePressTime;
    private static final long DOUBLE_TAP_TIME_MS = 300;  // Time window for double-tap
    
    // Player constants (Minecraft standard dimensions)
    private static final float PLAYER_EYE_HEIGHT = 1.62f;
    
    // Physics constants (tuned to feel like Minecraft)
    private static final float WALK_SPEED = 4.317f;      // Minecraft: 4.317 blocks/sec
    private static final float FLY_SPEED = 10.0f;        // Flying is faster
    private static final float JUMP_VELOCITY = 9.0f;      // Tuned for ~1.25 block jump
    private static final float GROUND_ACCELERATION = 0.1f; // How fast we reach max speed
    private static final float AIR_ACCELERATION = 0.02f;   // Air control (limited)
    private static final float FLY_ACCELERATION = 0.15f;   // Flying acceleration

    public Player() {
        // Spawn at Y=65 (one block above the grass at Y=64)
        position = new Vector3f(8, 65, 8);
        velocity = new Vector3f(0, 0, 0);
        // Start looking straight ahead (pitch=0, yaw=0)
        // Pitch: 0=straight ahead, positive=down, negative=up
        // Yaw: 0=north, 90=east, 180=south, 270=west
        rotation = new Vector3f(0, 0, 0);  // Looking straight north, level
        lastPosition = new Vector3f(position);
        lastRotation = new Vector3f(rotation);
        onGround = false;
        horizontalCollision = false;
        flying = false;
        lastSpacePressTime = 0;
    }

    /**
     * Simple physics update without collision (collision handled externally).
     * Just updates last position for interpolation.
     */
    public void updateLastState() {
        lastPosition.set(position);
        lastRotation.set(rotation);
    }
    
    /**
     * Set ground state (called by external collision system).
     */
    public void setOnGround(boolean onGround) {
        this.onGround = onGround;
    }

    /**
     * Apply movement input (called from input handling).
     * This adds to velocity, not sets it directly.
     */
    public void move(float forward, float right) {
        if (forward == 0 && right == 0) return;
        
        // Calculate movement direction based on camera rotation
        float yawRad = (float) Math.toRadians(rotation.y);
        float sinYaw = (float) Math.sin(yawRad);
        float cosYaw = (float) Math.cos(yawRad);
        
        // Calculate desired movement direction
        float moveX = sinYaw * forward + cosYaw * right;
        float moveZ = -cosYaw * forward + sinYaw * right;
        
        // Normalize diagonal movement (prevent faster diagonal movement)
        float length = (float) Math.sqrt(moveX * moveX + moveZ * moveZ);
        if (length > 0) {
            moveX /= length;
            moveZ /= length;
        }
        
        // Get current speed and acceleration based on state (flying/walking)
        float speed = getMovementSpeed();
        float acceleration = getAcceleration();
        
        // Add to velocity (acceleration-based, not instant)
        velocity.x += moveX * speed * acceleration;
        velocity.z += moveZ * speed * acceleration;
        
        // Cap horizontal speed
        float horizontalSpeed = (float) Math.sqrt(velocity.x * velocity.x + velocity.z * velocity.z);
        if (horizontalSpeed > speed) {
            float scale = speed / horizontalSpeed;
            velocity.x *= scale;
            velocity.z *= scale;
        }
    }

    /**
     * Jump if on ground.
     */
    public void jump() {
        if (onGround) {
            velocity.y = JUMP_VELOCITY;
            onGround = false;
        }
    }

    /**
     * Rotate player view (camera).
     */
    public void rotate(float dyaw, float dpitch) {
        rotation.y += dyaw;
        rotation.x += dpitch;

        // Clamp pitch to prevent over-rotation
        rotation.x = Math.max(-90, Math.min(90, rotation.x));
        
        // Normalize yaw to 0-360
        rotation.y = rotation.y % 360;
        if (rotation.y < 0) rotation.y += 360;
    }

    // Getters
    public Vector3f getPosition() { return position; }
    public Vector3f getLastPosition() { return lastPosition; }
    public Vector3f getRotation() { return rotation; }
    public Vector3f getLastRotation() { return lastRotation; }
    public Vector3f getVelocity() { return velocity; }
    public boolean isOnGround() { return onGround; }
    public boolean hasHorizontalCollision() { return horizontalCollision; }
    public float getEyeHeight() { return PLAYER_EYE_HEIGHT; }
    
    public boolean isFlying() { return flying; }
    
    /**
     * Toggle flying mode (called on double-tap space).
     */
    public void toggleFlying() {
        flying = !flying;
        if (flying) {
            // When starting to fly, zero vertical velocity
            velocity.y = 0;
        }
        System.out.println("✈️ FLYING MODE: " + (flying ? "ON" : "OFF"));
    }
    
    /**
     * Handle space bar press for jump/fly logic.
     * Returns true if this was a double-tap.
     */
    public boolean onSpacePressed() {
        long currentTime = System.currentTimeMillis();
        long timeSinceLastPress = currentTime - lastSpacePressTime;
        
        boolean isDoubleTap = timeSinceLastPress < DOUBLE_TAP_TIME_MS && timeSinceLastPress > 0;
        lastSpacePressTime = currentTime;
        
        if (isDoubleTap) {
            toggleFlying();
            return true;
        }
        return false;
    }
    
    /**
     * Get movement speed based on current state.
     */
    public float getMovementSpeed() {
        return flying ? FLY_SPEED : WALK_SPEED;
    }
    
    /**
     * Get acceleration based on current state.
     */
    public float getAcceleration() {
        if (flying) return FLY_ACCELERATION;
        return onGround ? GROUND_ACCELERATION : AIR_ACCELERATION;
    }
}
