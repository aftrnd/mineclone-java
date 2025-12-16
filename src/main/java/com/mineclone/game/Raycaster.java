package com.mineclone.game;

import org.joml.Vector3f;
import org.joml.Vector3i;

/**
 * Performs raycasting to detect blocks the player is looking at.
 * Based on Minecraft's ray tracing algorithm (DDA - Digital Differential Analyzer).
 * 
 * References Minecraft's Level.clip() and BlockHitResult system.
 */
public class Raycaster {
    private static final float MAX_REACH_DISTANCE = 5.0f;  // Minecraft's block reach (creative: 5, survival: 4.5)
    private static final float STEP_SIZE = 0.1f;  // How far to step along ray each iteration
    
    /**
     * Cast a ray from the camera to find the first solid block.
     * 
     * @param origin Camera position (player eye position)
     * @param direction Forward direction (normalized)
     * @param chunkManager World to raycast against
     * @return RaycastResult containing hit information, or miss if nothing hit
     */
    public static RaycastResult raycast(Vector3f origin, Vector3f direction, ChunkManager chunkManager) {
        Vector3f currentPos = new Vector3f(origin);
        Vector3f step = new Vector3f(direction).mul(STEP_SIZE);
        
        Vector3i lastBlockPos = null;
        float distance = 0;
        int stepsChecked = 0;
        
        // Step along the ray until we hit something or exceed max distance
        while (distance < MAX_REACH_DISTANCE) {
            // Current block position
            int blockX = (int) Math.floor(currentPos.x);
            int blockY = (int) Math.floor(currentPos.y);
            int blockZ = (int) Math.floor(currentPos.z);
            
            stepsChecked++;
            
            // Check if this block is solid
            if (chunkManager.isBlockSolidAt(blockX, blockY, blockZ)) {
                Vector3i hitBlockPos = new Vector3i(blockX, blockY, blockZ);
                
                // Calculate adjacent position (for block placing)
                Vector3i adjacentPos = lastBlockPos != null ? lastBlockPos : hitBlockPos;
                
                // Calculate face normal (which face was hit)
                Vector3i faceNormal = new Vector3i(0, 0, 0);
                if (lastBlockPos != null) {
                    faceNormal.x = hitBlockPos.x - lastBlockPos.x;
                    faceNormal.y = hitBlockPos.y - lastBlockPos.y;
                    faceNormal.z = hitBlockPos.z - lastBlockPos.z;
                }
                
                return new RaycastResult(
                    hitBlockPos,
                    adjacentPos,
                    new Vector3f(currentPos),
                    faceNormal
                );
            }
            
            // Remember last block position (for placing blocks)
            lastBlockPos = new Vector3i(blockX, blockY, blockZ);
            
            // Step forward
            currentPos.add(step);
            distance += STEP_SIZE;
        }
        
        // No hit
        return new RaycastResult();
    }
    
    /**
     * Get the max reach distance for block interaction.
     */
    public static float getMaxReachDistance() {
        return MAX_REACH_DISTANCE;
    }
}

