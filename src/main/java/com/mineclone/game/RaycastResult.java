package com.mineclone.game;

import org.joml.Vector3f;
import org.joml.Vector3i;

/**
 * Result of a raycast operation (similar to Minecraft's BlockHitResult).
 * Contains information about which block was hit and from which direction.
 */
public class RaycastResult {
    private final boolean hit;
    private final Vector3i blockPos;          // Position of the hit block
    private final Vector3i adjacentPos;       // Position adjacent to hit block (for placing)
    private final Vector3f hitPoint;          // Exact hit point in world space
    private final Vector3i faceNormal;        // Which face was hit (for placing blocks)
    
    // No hit constructor
    public RaycastResult() {
        this.hit = false;
        this.blockPos = null;
        this.adjacentPos = null;
        this.hitPoint = null;
        this.faceNormal = null;
    }
    
    // Hit constructor
    public RaycastResult(Vector3i blockPos, Vector3i adjacentPos, Vector3f hitPoint, Vector3i faceNormal) {
        this.hit = true;
        this.blockPos = blockPos;
        this.adjacentPos = adjacentPos;
        this.hitPoint = hitPoint;
        this.faceNormal = faceNormal;
    }
    
    public boolean isHit() {
        return hit;
    }
    
    public Vector3i getBlockPos() {
        return blockPos;
    }
    
    public Vector3i getAdjacentPos() {
        return adjacentPos;
    }
    
    public Vector3f getHitPoint() {
        return hitPoint;
    }
    
    public Vector3i getFaceNormal() {
        return faceNormal;
    }
}

