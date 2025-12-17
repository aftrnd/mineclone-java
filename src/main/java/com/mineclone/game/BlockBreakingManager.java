package com.mineclone.game;

/**
 * Manages block breaking progress and animation (Minecraft-style).
 * 
 * Minecraft's breaking system:
 * - Breaking takes time based on block hardness
 * - Progress shown via 10 stages of cracking overlay texture
 * - Breaking can be interrupted/cancelled
 * - Only one block can be broken at a time per player
 */
public class BlockBreakingManager {
    // Breaking state
    private int targetX, targetY, targetZ;  // Block currently being broken
    private boolean isBreaking;              // True if currently breaking a block
    private float breakProgress;             // Progress 0.0-1.0
    private float breakTime;                 // How long this block takes to break (seconds)
    private Block.Type breakingType;         // Type of block being broken
    
    public BlockBreakingManager() {
        reset();
    }
    
    /**
     * Start breaking a block.
     */
    public void startBreaking(int x, int y, int z, Block.Type type) {
        targetX = x;
        targetY = y;
        targetZ = z;
        isBreaking = true;
        breakProgress = 0.0f;
        breakTime = type.getHardness();  // Minecraft's hardness value
        breakingType = type;
        
        System.out.println(String.format("🔨 Started breaking %s at (%d,%d,%d) - takes %.1fs", 
            type, x, y, z, breakTime));
    }
    
    /**
     * Update breaking progress. Returns true if block should break.
     */
    public boolean updateBreaking(float deltaTime) {
        if (!isBreaking) {
            return false;
        }
        
        // Can't break air or water
        if (breakingType == Block.Type.AIR || breakingType == Block.Type.WATER) {
            reset();
            return false;
        }
        
        breakProgress += deltaTime / breakTime;
        
        // Block broken!
        if (breakProgress >= 1.0f) {
            System.out.println(String.format("💥 Block broken at (%d,%d,%d)", 
                targetX, targetY, targetZ));
            reset();
            return true;
        }
        
        return false;
    }
    
    /**
     * Cancel current breaking operation.
     */
    public void reset() {
        isBreaking = false;
        targetX = targetY = targetZ = 0;
        breakProgress = 0.0f;
        breakTime = 0.0f;
        breakingType = null;
    }
    
    /**
     * Check if currently breaking a block.
     */
    public boolean isBreakingAny() {
        return isBreaking;
    }
    
    /**
     * Check if currently breaking a specific block.
     */
    public boolean isBreaking(int x, int y, int z) {
        return isBreaking && targetX == x && targetY == y && targetZ == z;
    }
    
    /**
     * Get breaking stage (0-9) for rendering the overlay texture.
     * Minecraft uses 10 stages: destroy_stage_0.png through destroy_stage_9.png
     */
    public int getBreakingStage() {
        if (!isBreaking) {
            return -1;
        }
        return Math.min(9, (int)(breakProgress * 10));
    }
    
    /**
     * Get the block position currently being broken.
     */
    public int[] getTargetBlock() {
        return isBreaking ? new int[]{targetX, targetY, targetZ} : null;
    }
    
    /**
     * Get current break progress (0.0-1.0).
     */
    public float getBreakProgress() {
        return breakProgress;
    }
}

