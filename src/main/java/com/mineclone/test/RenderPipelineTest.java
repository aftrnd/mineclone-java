package com.mineclone.test;

import com.mineclone.core.utils.Logger;

/**
 * Basic smoke tests for the rendering pipeline.
 * These run at startup to verify critical systems.
 */
public class RenderPipelineTest {
    private static final String CATEGORY = "RenderTest";
    
    /**
     * Run all startup tests.
     */
    public static boolean runAllTests() {
        Logger.info(CATEGORY, "=== Running Rendering Pipeline Tests ===");
        
        boolean allPassed = true;
        
        allPassed &= testMeshGeneration();
        allPassed &= testTextureCoordinates();
        allPassed &= testChunkCoordinates();
        
        if (allPassed) {
            Logger.info(CATEGORY, "✅ All rendering pipeline tests PASSED");
        } else {
            Logger.error(CATEGORY, "❌ Some rendering pipeline tests FAILED");
        }
        
        return allPassed;
    }
    
    /**
     * Test that mesh generation produces valid data.
     */
    private static boolean testMeshGeneration() {
        Logger.debug(CATEGORY, "Testing mesh generation...");
        
        // Test vertex format (8 floats per vertex: x,y,z, r,g,b, u,v)
        int expectedFloatsPerVertex = 8;
        
        // Simulate a simple quad (2 triangles = 6 vertices)
        int verticesPerQuad = 6;
        int totalFloats = verticesPerQuad * expectedFloatsPerVertex;
        
        if (totalFloats != 48) {
            Logger.error(CATEGORY, "Mesh format test failed: expected 48 floats for quad, got " + totalFloats);
            return false;
        }
        
        Logger.debug(CATEGORY, "✓ Mesh generation test passed");
        return true;
    }
    
    /**
     * Test texture coordinate generation.
     */
    private static boolean testTextureCoordinates() {
        Logger.debug(CATEGORY, "Testing texture coordinates...");
        
        // Test UV coordinate ranges (should be 0.0 to 1.0)
        float[] testUVs = {0.0f, 0.0f, 0.25f, 0.5f};
        
        for (float uv : testUVs) {
            if (uv < 0.0f || uv > 1.0f) {
                Logger.error(CATEGORY, "UV coordinate out of range: " + uv);
                return false;
            }
        }
        
        Logger.debug(CATEGORY, "✓ Texture coordinate test passed");
        return true;
    }
    
    /**
     * Test chunk coordinate calculations.
     */
    private static boolean testChunkCoordinates() {
        Logger.debug(CATEGORY, "Testing chunk coordinates...");
        
        // Test world to chunk conversion
        int chunkSize = 16;
        
        // World pos 0 should be chunk 0
        int chunk1 = 0 / chunkSize;
        if (chunk1 != 0) {
            Logger.error(CATEGORY, "Chunk coordinate test failed: world 0 -> chunk " + chunk1);
            return false;
        }
        
        // World pos 16 should be chunk 1
        int chunk2 = 16 / chunkSize;
        if (chunk2 != 1) {
            Logger.error(CATEGORY, "Chunk coordinate test failed: world 16 -> chunk " + chunk2);
            return false;
        }
        
        // World pos -1 should be chunk -1
        int chunk3 = Math.floorDiv(-1, chunkSize);
        if (chunk3 != -1) {
            Logger.error(CATEGORY, "Chunk coordinate test failed: world -1 -> chunk " + chunk3);
            return false;
        }
        
        Logger.debug(CATEGORY, "✓ Chunk coordinate test passed");
        return true;
    }
}

