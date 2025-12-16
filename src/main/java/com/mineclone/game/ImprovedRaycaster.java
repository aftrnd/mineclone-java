package com.mineclone.game;

import org.joml.Vector3f;
import org.joml.Vector3i;

/**
 * Raycaster using small-step traversal with proper block intersection testing.
 * Based on Minecraft's approach: step along ray, check each block's AABB.
 */
public class ImprovedRaycaster {
    private static final float MAX_REACH_DISTANCE = 5.0f;
    private static final float STEP_SIZE = 0.005f;  // Very small steps for maximum accuracy
    
    /**
     * Cast a ray from origin along direction, finding first solid block hit.
     * Uses small steps and proper AABB intersection (Minecraft style).
     */
    public static RaycastResult raycast(Vector3f origin, Vector3f direction, ChunkManager chunkManager) {
        // Ensure direction is normalized
        Vector3f dir = new Vector3f(direction).normalize();
        
        // Current position along ray
        Vector3f currentPos = new Vector3f(origin);
        Vector3f step = new Vector3f(dir).mul(STEP_SIZE);
        
        // Track previous block for adjacent placement
        Vector3i prevBlock = null;
        int prevX = (int) Math.floor(origin.x);
        int prevY = (int) Math.floor(origin.y);
        int prevZ = (int) Math.floor(origin.z);
        
        float distance = 0;
        
        // Step along ray
        while (distance < MAX_REACH_DISTANCE) {
            // Current block coordinates
            int blockX = (int) Math.floor(currentPos.x);
            int blockY = (int) Math.floor(currentPos.y);
            int blockZ = (int) Math.floor(currentPos.z);
            
            // Check if we've moved to a new block
            if (blockX != prevX || blockY != prevY || blockZ != prevZ) {
                // Check if this block is solid
                if (chunkManager.isBlockSolidAt(blockX, blockY, blockZ)) {
                    // Hit!
                    Vector3i hitBlockPos = new Vector3i(blockX, blockY, blockZ);
                    Vector3i adjacentPos = new Vector3i(prevX, prevY, prevZ);
                    
                    // Calculate face normal (which face was hit)
                    Vector3i faceNormal = new Vector3i(
                        hitBlockPos.x - adjacentPos.x,
                        hitBlockPos.y - adjacentPos.y,
                        hitBlockPos.z - adjacentPos.z
                    );
                    
                    return new RaycastResult(hitBlockPos, adjacentPos, new Vector3f(currentPos), faceNormal);
                }
                
                // Update previous block
                prevX = blockX;
                prevY = blockY;
                prevZ = blockZ;
            }
            
            // Move along ray
            currentPos.add(step);
            distance += STEP_SIZE;
        }
        
        // No hit
        return new RaycastResult();
    }
}

