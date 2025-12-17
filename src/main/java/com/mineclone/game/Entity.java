package com.mineclone.game;

import org.joml.Vector3d;
import org.joml.Vector3f;

/**
 * Base Entity class - EXACT Minecraft implementation.
 * 
 * This follows Minecraft 1.21's entity system exactly:
 * - Double precision positions (x, y, z) with old positions (xo, yo, zo)
 * - Delta movement as velocity (blocks per tick)
 * - Axis-aligned bounding box collision
 * - Separate X/Y/Z collision passes
 * - Ground detection via vertical collision
 * 
 * Key Minecraft patterns:
 * - Position is at entity FEET (not center)
 * - Y-axis moves FIRST (prevents getting stuck on walls when jumping)
 * - All physics runs at 20 TPS (50ms per tick)
 * 
 * Based on net.minecraft.world.entity.Entity
 */
public abstract class Entity {
    
    // === POSITION STATE (doubles for precision) ===
    protected double x;      // Current position (feet)
    protected double y;      
    protected double z;
    
    protected double xo;     // Previous tick position (for interpolation)
    protected double yo;
    protected double zo;
    
    // === ROTATION STATE (floats) ===
    protected float xRot;    // Pitch (-90 to 90 degrees)
    protected float yRot;    // Yaw (0 to 360 degrees)
    protected float xRotO;   // Previous pitch
    protected float yRotO;   // Previous yaw
    
    // === VELOCITY ===
    // Minecraft calls this "deltaMovement" - it's velocity in blocks/tick
    protected Vector3d deltaMovement;
    
    // === COLLISION STATE ===
    protected boolean onGround;
    protected boolean horizontalCollision;
    protected boolean verticalCollision;
    protected boolean verticalCollisionBelow;  // Specific to hitting ground
    
    // === ENTITY DIMENSIONS ===
    protected float bbWidth;   // Bounding box width (X and Z)
    protected float bbHeight;  // Bounding box height (Y)
    protected float eyeHeight; // Eye height above feet
    
    // === PHYSICS PROPERTIES ===
    protected boolean noGravity;
    protected boolean noPhysics;  // Disable collision
    
    // World reference for collision checks
    protected transient ChunkManager world;
    
    /**
     * Create entity.
     */
    public Entity(ChunkManager world) {
        this.world = world;
        this.x = 0;
        this.y = 0;
        this.z = 0;
        this.xo = 0;
        this.yo = 0;
        this.zo = 0;
        this.xRot = 0;
        this.yRot = 0;
        this.xRotO = 0;
        this.yRotO = 0;
        this.deltaMovement = new Vector3d(0, 0, 0);
        this.onGround = false;
        this.horizontalCollision = false;
        this.verticalCollision = false;
        this.verticalCollisionBelow = false;
        this.noGravity = false;
        this.noPhysics = false;
        this.bbWidth = 0.6f;
        this.bbHeight = 1.8f;
        this.eyeHeight = 1.62f;
    }
    
    /**
     * Main tick method - called every game tick (20 TPS).
     */
    public void tick() {
        // Store old position for interpolation
        this.xo = this.x;
        this.yo = this.y;
        this.zo = this.z;
        this.xRotO = this.xRot;
        this.yRotO = this.yRot;
        
        // Subclasses override to add behavior
    }
    
    /**
     * Move entity with collision detection.
     * This is Minecraft's EXACT algorithm.
     * 
     * Key points:
     * - Moves Y first, then X, then Z
     * - Each axis checks collision independently
     * - Collision snaps to block boundaries
     * - Ground detection based on Y collision
     * 
     * @param movement Desired movement this tick (blocks)
     */
    public void move(Vector3d movement) {
        if (noPhysics) {
            // No collision - just move
            this.x += movement.x;
            this.y += movement.y;
            this.z += movement.z;
            return;
        }
        
        if (movement.x == 0 && movement.y == 0 && movement.z == 0) {
            return;
        }
        
        // Store original to detect collisions
        Vector3d original = new Vector3d(movement);
        
        // Debug logging
        boolean debug = Math.abs(movement.x) > 0.01 || Math.abs(movement.z) > 0.01;
        if (debug) {
            System.out.printf("🔍 BEFORE: pos=(%.3f, %.3f, %.3f) move=(%.3f, %.3f, %.3f)%n",
                x, y, z, movement.x, movement.y, movement.z);
        }
        
        // Reset collision flags
        this.horizontalCollision = false;
        this.verticalCollision = false;
        this.verticalCollisionBelow = false;
        
        // === MINECRAFT'S EXACT ORDER: Y -> X -> Z ===
        // Moving Y first prevents getting stuck on walls when jumping
        
        if (movement.y != 0) {
            movement.y = this.collideY(movement.y);
            this.y += movement.y;
        }
        
        if (movement.x != 0) {
            double beforeX = this.x;
            movement.x = this.collideX(movement.x);
            this.x += movement.x;
            if (debug && movement.x != original.x) {
                System.out.printf("   ❌ X collision: %.3f -> %.3f (wanted %.3f)%n",
                    beforeX, this.x, beforeX + original.x);
            }
        }
        
        if (movement.z != 0) {
            double beforeZ = this.z;
            movement.z = this.collideZ(movement.z);
            this.z += movement.z;
            if (debug && movement.z != original.z) {
                System.out.printf("   ❌ Z collision: %.3f -> %.3f (wanted %.3f)%n",
                    beforeZ, this.z, beforeZ + original.z);
            }
        }
        
        // Detect collision types
        this.horizontalCollision = (movement.x != original.x || movement.z != original.z);
        this.verticalCollision = (movement.y != original.y);
        
        // Ground detection: only if moving down and stopped
        this.onGround = this.verticalCollisionBelow || (this.verticalCollision && original.y < 0);
        
        // Stop vertical velocity on collision
        if (this.onGround && this.deltaMovement.y < 0) {
            this.deltaMovement.y = 0;
        }
        
        if (this.verticalCollision && original.y > 0) {
            this.deltaMovement.y = 0;
        }
        
        // Horizontal collision: Don't zero velocity completely!
        // In Minecraft, you can slide along walls. Only dampen velocity that's
        // directly into the wall, and let friction handle the rest.
        if (this.horizontalCollision) {
            boolean xBlocked = (movement.x != original.x);
            boolean zBlocked = (movement.z != original.z);
            
            // Corner case: both X and Z blocked - stuck in corner
            // Try to slide along the less-blocked direction
            if (xBlocked && zBlocked) {
                // Reduce both but don't completely stop
                this.deltaMovement.x *= 0.5;
                this.deltaMovement.z *= 0.5;
                
                // Nudge slightly away from exact corner to prevent flickering
                double nudge = 0.001;
                if (Math.abs(this.x - Math.round(this.x)) < 0.1) {
                    this.x += (this.deltaMovement.x > 0 ? nudge : -nudge);
                }
                if (Math.abs(this.z - Math.round(this.z)) < 0.1) {
                    this.z += (this.deltaMovement.z > 0 ? nudge : -nudge);
                }
            } else {
                // Single axis blocked - reduce that axis only
                if (xBlocked) this.deltaMovement.x *= 0.8;
                if (zBlocked) this.deltaMovement.z *= 0.8;
            }
        }
    }
    
    /**
     * Collide on Y axis (vertical).
     * Returns the actual movement allowed after collision.
     */
    private double collideY(double dy) {
        // Calculate bounding box after movement
        double minX = this.x - this.bbWidth / 2.0;
        double maxX = this.x + this.bbWidth / 2.0;
        double minZ = this.z - this.bbWidth / 2.0;
        double maxZ = this.z + this.bbWidth / 2.0;
        
        double newY = this.y + dy;
        double minY = newY;
        double maxY = newY + this.bbHeight;
        
        // Block range to check
        int blockMinX = (int) Math.floor(minX);
        int blockMaxX = (int) Math.floor(maxX);
        int blockMinZ = (int) Math.floor(minZ);
        int blockMaxZ = (int) Math.floor(maxZ);
        
        int blockMinY, blockMaxY;
        if (dy < 0) {
            // Moving down - check below
            blockMinY = (int) Math.floor(minY);
            blockMaxY = (int) Math.floor(this.y);
        } else {
            // Moving up - check above
            blockMinY = (int) Math.floor(this.y + this.bbHeight);
            blockMaxY = (int) Math.floor(maxY);
        }
        
        // Scan for solid blocks
        for (int bx = blockMinX; bx <= blockMaxX; bx++) {
            for (int by = blockMinY; by <= blockMaxY; by++) {
                for (int bz = blockMinZ; bz <= blockMaxZ; bz++) {
                    if (world != null && world.isBlockSolidAt(bx, by, bz)) {
                        // Collision detected!
                        if (dy < 0) {
                            // Hit ground - snap feet to top of block
                            this.verticalCollisionBelow = true;
                            return (by + 1) - this.y;
                        } else {
                            // Hit ceiling - snap head to bottom of block
                            return by - (this.y + this.bbHeight);
                        }
                    }
                }
            }
        }
        
        return dy;  // No collision
    }
    
    /**
     * Collide on X axis (east/west).
     * Improved to handle edges better with small epsilon.
     */
    private double collideX(double dx) {
        // Add small epsilon to prevent exact edge cases
        double epsilon = 0.0001;
        
        double minZ = this.z - this.bbWidth / 2.0 + epsilon;
        double maxZ = this.z + this.bbWidth / 2.0 - epsilon;
        double minY = this.y + epsilon;
        double maxY = this.y + this.bbHeight - epsilon;
        
        double newX = this.x + dx;
        double minX = newX - this.bbWidth / 2.0;
        double maxX = newX + this.bbWidth / 2.0;
        
        // Expand block check range slightly to catch corner cases
        int blockMinZ = (int) Math.floor(minZ - epsilon);
        int blockMaxZ = (int) Math.floor(maxZ + epsilon);
        int blockMinY = (int) Math.floor(minY);
        int blockMaxY = (int) Math.floor(maxY);
        
        int blockMinX, blockMaxX;
        if (dx < 0) {
            blockMinX = (int) Math.floor(minX);
            blockMaxX = (int) Math.floor(this.x - this.bbWidth / 2.0);
        } else {
            blockMinX = (int) Math.floor(this.x + this.bbWidth / 2.0);
            blockMaxX = (int) Math.floor(maxX);
        }
        
        for (int bx = blockMinX; bx <= blockMaxX; bx++) {
            for (int by = blockMinY; by <= blockMaxY; by++) {
                for (int bz = blockMinZ; bz <= blockMaxZ; bz++) {
                    if (world != null && world.isBlockSolidAt(bx, by, bz)) {
                        if (dx < 0) {
                            // Add small buffer to prevent sticking
                            return (bx + 1 + epsilon) - (this.x - this.bbWidth / 2.0);
                        } else {
                            return (bx - epsilon) - (this.x + this.bbWidth / 2.0);
                        }
                    }
                }
            }
        }
        
        return dx;
    }
    
    /**
     * Collide on Z axis (north/south).
     * Improved to handle edges better with small epsilon.
     */
    private double collideZ(double dz) {
        // Add small epsilon to prevent exact edge cases
        double epsilon = 0.0001;
        
        double minX = this.x - this.bbWidth / 2.0 + epsilon;
        double maxX = this.x + this.bbWidth / 2.0 - epsilon;
        double minY = this.y + epsilon;
        double maxY = this.y + this.bbHeight - epsilon;
        
        double newZ = this.z + dz;
        double minZ = newZ - this.bbWidth / 2.0;
        double maxZ = newZ + this.bbWidth / 2.0;
        
        // Expand block check range slightly to catch corner cases
        int blockMinX = (int) Math.floor(minX - epsilon);
        int blockMaxX = (int) Math.floor(maxX + epsilon);
        int blockMinY = (int) Math.floor(minY);
        int blockMaxY = (int) Math.floor(maxY);
        
        int blockMinZ, blockMaxZ;
        if (dz < 0) {
            blockMinZ = (int) Math.floor(minZ);
            blockMaxZ = (int) Math.floor(this.z - this.bbWidth / 2.0);
        } else {
            blockMinZ = (int) Math.floor(this.z + this.bbWidth / 2.0);
            blockMaxZ = (int) Math.floor(maxZ);
        }
        
        for (int bx = blockMinX; bx <= blockMaxX; bx++) {
            for (int by = blockMinY; by <= blockMaxY; by++) {
                for (int bz = blockMinZ; bz <= blockMaxZ; bz++) {
                    if (world != null && world.isBlockSolidAt(bx, by, bz)) {
                        if (dz < 0) {
                            // Add small buffer to prevent sticking
                            return (bz + 1 + epsilon) - (this.z - this.bbWidth / 2.0);
                        } else {
                            return (bz - epsilon) - (this.z + this.bbWidth / 2.0);
                        }
                    }
                }
            }
        }
        
        return dz;
    }
    
    /**
     * Set entity position directly.
     */
    public void setPos(double x, double y, double z) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.xo = x;
        this.yo = y;
        this.zo = z;
    }
    
    /**
     * Set entity rotation.
     */
    public void setRot(float yRot, float xRot) {
        this.yRot = yRot;
        this.xRot = xRot;
        this.yRotO = yRot;
        this.xRotO = xRot;
    }
    
    /**
     * Add to rotation (for mouse look).
     */
    public void turn(float dyRot, float dxRot) {
        this.yRot += dyRot;
        this.xRot += dxRot;
        
        // Clamp pitch to -90/+90
        this.xRot = Math.max(-90f, Math.min(90f, this.xRot));
        
        // Normalize yaw to 0-360
        this.yRot = this.yRot % 360f;
        if (this.yRot < 0) this.yRot += 360f;
    }
    
    /**
     * Get interpolated position for rendering.
     */
    public Vector3d getPosition(float partialTick) {
        return new Vector3d(
            lerp(this.xo, this.x, partialTick),
            lerp(this.yo, this.y, partialTick),
            lerp(this.zo, this.z, partialTick)
        );
    }
    
    /**
     * Get interpolated rotation.
     */
    public Vector3f getRotation(float partialTick) {
        return new Vector3f(
            lerpAngle(this.xRotO, this.xRot, partialTick),
            lerpAngle(this.yRotO, this.yRot, partialTick),
            0
        );
    }
    
    /**
     * Get eye position (for camera).
     */
    public Vector3d getEyePosition(float partialTick) {
        Vector3d pos = getPosition(partialTick);
        pos.y += this.eyeHeight;
        return pos;
    }
    
    /**
     * Get forward direction vector.
     */
    public Vector3f getForward() {
        float yawRad = (float) Math.toRadians(this.yRot);
        float pitchRad = (float) Math.toRadians(this.xRot);
        
        float cosPitch = (float) Math.cos(pitchRad);
        return new Vector3f(
            (float) Math.sin(yawRad) * cosPitch,
            -(float) Math.sin(pitchRad),
            -(float) Math.cos(yawRad) * cosPitch
        ).normalize();
    }
    
    // === HELPER METHODS ===
    
    private double lerp(double start, double end, float alpha) {
        return start + (end - start) * alpha;
    }
    
    private float lerpAngle(float start, float end, float alpha) {
        float diff = end - start;
        while (diff > 180) diff -= 360;
        while (diff < -180) diff += 360;
        return start + diff * alpha;
    }
    
    // === GETTERS ===
    
    public double getX() { return x; }
    public double getY() { return y; }
    public double getZ() { return z; }
    public float getXRot() { return xRot; }
    public float getYRot() { return yRot; }
    public Vector3d getDeltaMovement() { return deltaMovement; }
    public boolean isOnGround() { return onGround; }
    public boolean hasHorizontalCollision() { return horizontalCollision; }
    public float getEyeHeight() { return eyeHeight; }
    public float getBbWidth() { return bbWidth; }
    public float getBbHeight() { return bbHeight; }
    
    // === SETTERS ===
    
    public void setDeltaMovement(Vector3d deltaMovement) {
        this.deltaMovement = deltaMovement;
    }
    
    public void setDeltaMovement(double x, double y, double z) {
        this.deltaMovement.set(x, y, z);
    }
    
    public void addDeltaMovement(double x, double y, double z) {
        this.deltaMovement.add(x, y, z);
    }
}
