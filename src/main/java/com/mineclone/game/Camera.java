package com.mineclone.game;

import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3d;
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
    
    // Tracked entity (using new Entity system)
    private Entity trackedEntity;
    
    // Interpolation for smooth rendering
    private float partialTick;
    
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
        
        updateViewMatrix();
    }

    /**
     * Setup camera for this frame (Minecraft-style).
     * Called every render frame with interpolation factor.
     * 
     * @param entity The entity to track (usually LocalPlayer)
     * @param partialTick Interpolation factor (0.0 to 1.0) between physics ticks
     */
    public void setup(Entity entity, float partialTick) {
        this.trackedEntity = entity;
        this.partialTick = partialTick;
        this.initialized = true;
        
        // Get interpolated eye position from entity
        Vector3d eyePos = entity.getEyePosition(partialTick);
        position.set((float)eyePos.x, (float)eyePos.y, (float)eyePos.z);
        
        // Get interpolated rotation from entity
        Vector3f entityRot = entity.getRotation(partialTick);
        rotation.set(entityRot.x, entityRot.y, 0); // No roll
        
        // Update quaternion from Euler angles
        updateQuaternion();
        
        // Update direction vectors
        updateDirectionVectors();
        
        // Rebuild view matrix
        updateViewMatrix();
    }
    
    /**
     * Setup camera with view bobbing (Minecraft-style walking animation).
     * 
     * @param entity The entity to track
     * @param partialTick Interpolation factor
     * @param enableViewBobbing Whether to apply view bobbing effect
     */
    public void setup(Entity entity, float partialTick, boolean enableViewBobbing) {
        // Standard setup
        setup(entity, partialTick);
        
        // Apply view bobbing if enabled and entity is a LivingEntity
        // BUT NOT when flying! (Minecraft disables bobbing when flying)
        if (enableViewBobbing && entity instanceof LivingEntity) {
            LivingEntity livingEntity = (LivingEntity) entity;
            if (!livingEntity.isFlying()) {  // Disable when flying!
                applyViewBobbing(livingEntity, partialTick);
            }
        }
    }

    /**
     * Simple setup without interpolation (for when entity doesn't move).
     */
    public void setupSimple(Entity entity) {
        setup(entity, 1.0f); // Full interpolation = use current state
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
     * Apply view bobbing effect (Minecraft's exact walking animation).
     * This creates the head bob when walking.
     * 
     * Based on net.minecraft.client.renderer.GameRenderer.bobView()
     * 
     * @param entity The living entity (player)
     * @param partialTick Interpolation factor
     */
    private void applyViewBobbing(LivingEntity entity, float partialTick) {
        // Get interpolated walk distance and bob amount (BOTH must be interpolated!)
        float walkDist = entity.getWalkDistance(partialTick);
        float bob = entity.getBob(partialTick);
        
        
        // No bobbing if bob amount is zero
        if (bob <= 0.0001f) return;
        
        // === MINECRAFT'S EXACT BOBBING FORMULA ===
        // Uses sine/cosine of walk distance to create rhythmic motion
        
        float walkDistPi = walkDist * (float) Math.PI;
        
        // Calculate bob offsets
        float sinWalk = (float) Math.sin(walkDistPi);
        float cosWalk = (float) Math.cos(walkDistPi);
        float absNegCosWalk = -(float) Math.abs(cosWalk);
        
        // Translation offset (side-to-side and up-down bobbing)
        // Minecraft's scale - tune to match the feel
        float bobScale = 0.05f;
        float bobX = sinWalk * bob * 0.5f * bobScale;
        float bobY = absNegCosWalk * bob * bobScale;
        
        // Rotation angles (head tilt during walking)
        float rollAngle = sinWalk * bob * 3.0f * bobScale;
        float pitchAngle = (float) Math.abs(Math.cos(walkDistPi - 0.2f)) * bob * 5.0f * bobScale;
        
        // Apply bobbing to view matrix
        // Order matters: translate first, then rotate
        viewMatrix.translate(bobX, bobY, 0);
        viewMatrix.rotateZ((float) Math.toRadians(rollAngle));
        viewMatrix.rotateX((float) Math.toRadians(pitchAngle));
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
    public Entity getTrackedEntity() { return trackedEntity; }
    
    public void setDetached(boolean detached) { this.detached = detached; }
}