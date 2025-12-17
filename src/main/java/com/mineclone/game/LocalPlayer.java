package com.mineclone.game;

/**
 * LocalPlayer - The player controlled by this client.
 * 
 * Handles:
 * - Input processing (keyboard/mouse)
 * - Creative flying toggle
 * - Client-side player behavior
 * 
 * Based on net.minecraft.client.player.LocalPlayer
 */
public class LocalPlayer extends LivingEntity {
    
    // === INPUT STATE ===
    private boolean moveForward;
    private boolean moveBackward;
    private boolean moveLeft;
    private boolean moveRight;
    private boolean moveUp;     // Flying up
    private boolean moveDown;   // Flying down
    private boolean wantsJump;
    private boolean wantsSprint;
    
    // === FLYING TOGGLE ===
    private long lastSpacePressTime;
    private static final long DOUBLE_TAP_WINDOW_MS = 300;
    
    // === PLAYER DIMENSIONS (Minecraft exact) ===
    private static final float PLAYER_WIDTH = 0.6f;
    private static final float PLAYER_HEIGHT = 1.8f;
    private static final float PLAYER_EYE_HEIGHT = 1.62f;
    
    // === VIEW BOBBING (Minecraft's xBob/yBob system) ===
    private float xBob;      // Pitch bob (current)
    private float xBobO;     // Pitch bob (previous)
    private float yBob;      // Yaw bob (current)
    private float yBobO;     // Yaw bob (previous)
    
    /**
     * Create local player.
     */
    public LocalPlayer(ChunkManager world) {
        super(world);
        
        // Set player dimensions
        this.bbWidth = PLAYER_WIDTH;
        this.bbHeight = PLAYER_HEIGHT;
        this.eyeHeight = PLAYER_EYE_HEIGHT;
        
        // Initialize input state
        this.moveForward = false;
        this.moveBackward = false;
        this.moveLeft = false;
        this.moveRight = false;
        this.moveUp = false;
        this.moveDown = false;
        this.wantsJump = false;
        this.wantsSprint = false;
        this.lastSpacePressTime = 0;
        this.xBob = 0;
        this.xBobO = 0;
        this.yBob = 0;
        this.yBobO = 0;
    }
    
    /**
     * Main player tick.
     * Processes input, then calls parent tick for physics.
     */
    @Override
    public void tick() {
        // Update view bobbing (Minecraft's EXACT method from LocalPlayer.aiStep)
        this.yBobO = this.yBob;
        this.xBobO = this.xBob;
        this.xBob = this.xBob + (this.getXRot() - this.xBob) * 0.5f;
        this.yBob = this.yBob + (this.getYRot() - this.yBob) * 0.5f;
        
        // Process keyboard input into movement values
        this.updateInputs();
        
        // Call parent tick (handles all physics)
        super.tick();
    }
    
    /**
     * Convert keyboard state into movement input (xxa, yya, zza).
     * This is called before physics runs.
     */
    private void updateInputs() {
        // Forward/backward input (negated to match Minecraft coordinate system)
        float forward = 0;
        if (this.moveForward) forward -= 1.0f;   // W key = negative (forward in -Z)
        if (this.moveBackward) forward += 1.0f;  // S key = positive (backward in +Z)
        
        // Strafe left/right input (negated for correct direction)
        float strafe = 0;
        if (this.moveLeft) strafe -= 1.0f;       // A key = negative (strafe left)
        if (this.moveRight) strafe += 1.0f;      // D key = positive (strafe right)
        
        // Vertical input (flying only)
        float vertical = 0;
        if (this.flying) {
            if (this.moveUp) vertical += 1.0f;
            if (this.moveDown) vertical -= 1.0f;
        }
        
        // Set movement input (LivingEntity will process this)
        this.setMovementInput(strafe, vertical, forward);
        
        // Update jump state
        this.setJumping(this.wantsJump);
        
        // Update sprint state
        this.setSprinting(this.wantsSprint);
    }
    
    /**
     * Update input state from keyboard.
     * Called by Game every frame.
     */
    public void setInput(boolean forward, boolean backward, boolean left, boolean right,
                        boolean jump, boolean sprint, boolean up, boolean down) {
        this.moveForward = forward;
        this.moveBackward = backward;
        this.moveLeft = left;
        this.moveRight = right;
        this.wantsJump = jump;
        this.wantsSprint = sprint;
        this.moveUp = up;
        this.moveDown = down;
    }
    
    /**
     * Handle space bar press for jump/flying toggle.
     * @return true if flying was toggled
     */
    public boolean onSpacePress() {
        long currentTime = System.currentTimeMillis();
        long timeSinceLastPress = currentTime - this.lastSpacePressTime;
        
        boolean isDoubleTap = (timeSinceLastPress < DOUBLE_TAP_WINDOW_MS && timeSinceLastPress > 0);
        this.lastSpacePressTime = currentTime;
        
        if (isDoubleTap) {
            this.toggleFlying();
            return true;
        }
        
        return false;
    }
    
    /**
     * Toggle creative flying mode.
     */
    private void toggleFlying() {
        this.setFlying(!this.flying);
        System.out.println("✈️ Flying: " + (this.flying ? "ON" : "OFF"));
    }
    
    /**
     * Handle mouse movement for camera rotation.
     */
    public void handleMouseLook(float deltaX, float deltaY, float sensitivity) {
        this.turn(deltaX * sensitivity, deltaY * sensitivity);
    }
    
    // === CONVENIENCE GETTERS (for backward compatibility) ===
    
    public org.joml.Vector3f getPositionVec3f() {
        return new org.joml.Vector3f((float)this.x, (float)this.y, (float)this.z);
    }
    
    public org.joml.Vector3f getLastPositionVec3f() {
        return new org.joml.Vector3f((float)this.xo, (float)this.yo, (float)this.zo);
    }
    
    public org.joml.Vector3f getRotationVec3f() {
        return new org.joml.Vector3f(this.xRot, this.yRot, 0);
    }
    
    public org.joml.Vector3f getLastRotationVec3f() {
        return new org.joml.Vector3f(this.xRotO, this.yRotO, 0);
    }
    
    public org.joml.Vector3f getVelocityVec3f() {
        return new org.joml.Vector3f(
            (float)this.deltaMovement.x,
            (float)this.deltaMovement.y,
            (float)this.deltaMovement.z
        );
    }
    
    // === VIEW BOBBING GETTERS (Minecraft's xBob/yBob system) ===
    
    /**
     * Get interpolated pitch bob for smooth view bobbing.
     */
    public float getXBob(float partialTick) {
        return this.xBobO + (this.xBob - this.xBobO) * partialTick;
    }
    
    /**
     * Get interpolated yaw bob for smooth view bobbing.
     */
    public float getYBob(float partialTick) {
        return this.yBobO + (this.yBob - this.yBobO) * partialTick;
    }
}
