package com.mineclone.game;

import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * First-person camera system following Minecraft's entity-based implementation.
 * 
 * Key features:
 * - Entity-based tracking (follows player automatically)
 * - Smooth interpolation between physics ticks
 * - Quaternion-based rotation (no gimbal lock)
 * - Forward/up/left direction vectors for calculations
 * - Eye height tracking with smooth transitions
 * 
 * Based on net.minecraft.client.Camera
 */
public class Camera {
    // Camera state
    private Vector3f position;
    private Vector3f rotation;  // pitch, yaw, roll (Euler angles for user convenience)
    private Quaternionf quaternion; // Actual rotation for rendering
    
    // Direction vectors (updated with rotation)
    private Vector3f forward;
    private Vector3f up;
    private Vector3f left;
    
    // View matrix (cached)
    private Matrix4f viewMatrix;
    
    // Tracked entity (player)
    private Player trackedPlayer;
    
    // Interpolation for smooth rendering
    private float partialTick;
    
    // Eye height interpolation (for crouching, etc.)
    private float eyeHeight;
    private float eyeHeightOld;
    
    // Camera modes
    private boolean initialized;
    private boolean detached; // Third-person mode

    public Camera() {
        position = new Vector3f(8, 67.62f, 8);
        rotation = new Vector3f(0, 0, 0);
        quaternion = new Quaternionf();
        
        // Initialize direction vectors
        forward = new Vector3f(0, 0, -1); // Looking down -Z by default
        up = new Vector3f(0, 1, 0);
        left = new Vector3f(-1, 0, 0);
        
        viewMatrix = new Matrix4f();
        initialized = false;
        detached = false;
        
        eyeHeight = 1.62f; // Minecraft player eye height
        eyeHeightOld = eyeHeight;
        
        updateViewMatrix();
    }

    /**
     * Setup camera for this frame (Minecraft-style).
     * Called every render frame with interpolation factor.
     * 
     * @param player The player to track
     * @param partialTick Interpolation factor (0.0 to 1.0) between physics ticks
     */
    public void setup(Player player, float partialTick) {
        this.trackedPlayer = player;
        this.partialTick = partialTick;
        this.initialized = true;
        
        // Interpolate position between last and current physics tick
        Vector3f currentPos = player.getPosition();
        Vector3f lastPos = player.getLastPosition();
        
        // Lerp position for smooth movement
        float interpX = lerp(lastPos.x, currentPos.x, partialTick);
        float interpY = lerp(lastPos.y, currentPos.y, partialTick);
        float interpZ = lerp(lastPos.z, currentPos.z, partialTick);
        
        // Interpolate eye height for smooth transitions
        float interpEyeHeight = lerp(eyeHeightOld, eyeHeight, partialTick);
        
        // Set camera position at eye level
        position.set(interpX, interpY + interpEyeHeight, interpZ);
        
        // Interpolate rotation
        Vector3f currentRot = player.getRotation();
        Vector3f lastRot = player.getLastRotation();
        
        rotation.x = lerpAngle(lastRot.x, currentRot.x, partialTick);
        rotation.y = lerpAngle(lastRot.y, currentRot.y, partialTick);
        rotation.z = 0; // No roll
        
        // Update quaternion from Euler angles
        updateQuaternion();
        
        // Update direction vectors
        updateDirectionVectors();
        
        // Rebuild view matrix
        updateViewMatrix();
    }

    /**
     * Simple setup without interpolation (for when player doesn't move).
     */
    public void setupSimple(Player player) {
        setup(player, 1.0f); // Full interpolation = use current state
    }

    /**
     * Update quaternion from Euler angles.
     * Order: Y (yaw) -> X (pitch) -> Z (roll)
     */
    private void updateQuaternion() {
        quaternion.identity();
        
        // Minecraft FPS camera: yaw around Y, then pitch around X
        // Positive yaw = turn right, positive pitch = look down
        // IMPORTANT: Negate pitch to match OpenGL/Minecraft convention
        quaternion.rotateY((float) Math.toRadians(rotation.y));
        quaternion.rotateX((float) Math.toRadians(-rotation.x));  // Negated for correct pitch direction
    }

    /**
     * Update forward, up, left direction vectors from pitch/yaw (Minecraft-style).
     * Direct calculation without quaternions for accuracy.
     */
    private void updateDirectionVectors() {
        // Convert to radians
        float yawRad = (float) Math.toRadians(rotation.y);
        float pitchRad = (float) Math.toRadians(rotation.x);
        
        // Calculate forward vector directly (Minecraft coordinate system)
        // Yaw 0 = North (-Z), Yaw 90 = East (+X)
        // Pitch 0 = Level, Pitch 90 = Down (-Y)
        float cosPitch = (float) Math.cos(pitchRad);
        forward.x = (float) Math.sin(yawRad) * cosPitch;
        forward.y = -(float) Math.sin(pitchRad);
        forward.z = -(float) Math.cos(yawRad) * cosPitch;
        forward.normalize();
        
        // Up vector - world up
        up.set(0, 1, 0);
        
        // Left vector (cross product: forward x up = left)
        forward.cross(up, left);
        left.normalize();
    }

    /**
     * Build view matrix from camera state.
     * Standard FPS camera: Pitch -> Yaw -> Translate
     */
    private void updateViewMatrix() {
        viewMatrix.identity();
        
        // Rotate by pitch (X-axis) - look up/down
        viewMatrix.rotateX((float) Math.toRadians(rotation.x));
        
        // Rotate by yaw (Y-axis) - look left/right  
        viewMatrix.rotateY((float) Math.toRadians(rotation.y));
        
        // Translate (negative because we move world, not camera)
        viewMatrix.translate(-position.x, -position.y, -position.z);
    }

    /**
     * Linear interpolation helper.
     */
    private float lerp(float start, float end, float alpha) {
        return start + (end - start) * alpha;
    }

    /**
     * Angle interpolation (handles wraparound correctly).
     */
    private float lerpAngle(float start, float end, float alpha) {
        // Handle wraparound (e.g., 350° to 10° should go through 0°, not 180°)
        float diff = end - start;
        while (diff > 180) diff -= 360;
        while (diff < -180) diff += 360;
        
        return start + diff * alpha;
    }

    /**
     * Set eye height (for crouching, swimming, etc.).
     */
    public void setEyeHeight(float height) {
        this.eyeHeightOld = this.eyeHeight;
        this.eyeHeight = height;
    }

    /**
     * Set rotation directly (for manual camera control).
     */
    public void setRotation(float pitch, float yaw, float roll) {
        rotation.set(pitch, yaw, roll);
        updateQuaternion();
        updateDirectionVectors();
        updateViewMatrix();
    }

    // Getters
    public Vector3f getPosition() { return position; }
    public Vector3f getRotation() { return rotation; }
    public Quaternionf getQuaternion() { return quaternion; }
    public Matrix4f getViewMatrix() { return viewMatrix; }
    
    public Vector3f getForward() { return forward; }
    public Vector3f getUp() { return up; }
    public Vector3f getLeft() { return left; }
    
    public boolean isInitialized() { return initialized; }
    public boolean isDetached() { return detached; }
    public Player getTrackedPlayer() { return trackedPlayer; }
    
    public void setDetached(boolean detached) { this.detached = detached; }
}