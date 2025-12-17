package com.mineclone.core.utils;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;

/**
 * Runtime health checks for the rendering pipeline.
 * Validates OpenGL state and catches common issues.
 */
public class RenderHealthCheck {
    private static final String CATEGORY = "RenderHealth";
    
    /**
     * Check for OpenGL errors and log them.
     * Call this after important OpenGL operations.
     */
    public static boolean checkGLError(String operation) {
        int error = glGetError();
        if (error != GL_NO_ERROR) {
            String errorStr = switch (error) {
                case GL_INVALID_ENUM -> "GL_INVALID_ENUM";
                case GL_INVALID_VALUE -> "GL_INVALID_VALUE";
                case GL_INVALID_OPERATION -> "GL_INVALID_OPERATION";
                case GL_STACK_OVERFLOW -> "GL_STACK_OVERFLOW";
                case GL_STACK_UNDERFLOW -> "GL_STACK_UNDERFLOW";
                case GL_OUT_OF_MEMORY -> "GL_OUT_OF_MEMORY";
                default -> "UNKNOWN_ERROR_" + error;
            };
            Logger.error(CATEGORY, "OpenGL error during '" + operation + "': " + errorStr);
            return false;
        }
        return true;
    }
    
    /**
     * Validate shader program.
     */
    public static boolean validateShaderProgram(int programId, String programName) {
        if (programId <= 0) {
            Logger.error(CATEGORY, "Invalid shader program ID: " + programId + " (" + programName + ")");
            return false;
        }
        
        int[] status = new int[1];
        glGetProgramiv(programId, GL_LINK_STATUS, status);
        if (status[0] == GL_FALSE) {
            String log = glGetProgramInfoLog(programId);
            Logger.error(CATEGORY, "Shader program '" + programName + "' link failed: " + log);
            return false;
        }
        
        glGetProgramiv(programId, GL_VALIDATE_STATUS, status);
        if (status[0] == GL_FALSE) {
            String log = glGetProgramInfoLog(programId);
            Logger.warn(CATEGORY, "Shader program '" + programName + "' validation warning: " + log);
        }
        
        Logger.debug(CATEGORY, "Shader program '" + programName + "' is valid (ID: " + programId + ")");
        return true;
    }
    
    /**
     * Validate texture.
     */
    public static boolean validateTexture(int textureId, String textureName) {
        if (textureId <= 0) {
            Logger.error(CATEGORY, "Invalid texture ID: " + textureId + " (" + textureName + ")");
            return false;
        }
        
        if (!glIsTexture(textureId)) {
            Logger.error(CATEGORY, "Texture ID " + textureId + " (" + textureName + ") is not a valid texture");
            return false;
        }
        
        Logger.debug(CATEGORY, "Texture '" + textureName + "' is valid (ID: " + textureId + ")");
        return true;
    }
    
    /**
     * Validate VBO.
     * Note: Newly created VBOs aren't "valid" until first bind, so we only check ID > 0
     */
    public static boolean validateVBO(int vboId, String vboName) {
        if (vboId <= 0) {
            Logger.error(CATEGORY, "Invalid VBO ID: " + vboId + " (" + vboName + ")");
            return false;
        }
        
        // Note: glIsBuffer() returns false for newly generated buffers until first bind
        // So we just verify the ID is positive, which means glGenBuffers() succeeded
        Logger.debug(CATEGORY, "VBO '" + vboName + "' created (ID: " + vboId + ")");
        return true;
    }
    
    /**
     * Validate VAO.
     * Note: Newly created VAOs aren't "valid" until first bind, so we only check ID > 0
     */
    public static boolean validateVAO(int vaoId, String vaoName) {
        if (vaoId <= 0) {
            Logger.error(CATEGORY, "Invalid VAO ID: " + vaoId + " (" + vaoName + ")");
            return false;
        }
        
        // Note: glIsVertexArray() returns false for newly generated VAOs until first bind
        // So we just verify the ID is positive, which means glGenVertexArrays() succeeded
        Logger.debug(CATEGORY, "VAO '" + vaoName + "' created (ID: " + vaoId + ")");
        return true;
    }
    
    /**
     * Check OpenGL capabilities.
     */
    public static void checkOpenGLCapabilities() {
        Logger.info(CATEGORY, "=== OpenGL Capabilities Check ===");
        Logger.info(CATEGORY, "Version: " + glGetString(GL_VERSION));
        Logger.info(CATEGORY, "Vendor: " + glGetString(GL_VENDOR));
        Logger.info(CATEGORY, "Renderer: " + glGetString(GL_RENDERER));
        
        // Check required features
        boolean depthTest = glIsEnabled(GL_DEPTH_TEST);
        boolean blending = glIsEnabled(GL_BLEND);
        boolean culling = glIsEnabled(GL_CULL_FACE);
        
        Logger.info(CATEGORY, "Depth test enabled: " + depthTest);
        Logger.info(CATEGORY, "Blending enabled: " + blending);
        Logger.info(CATEGORY, "Face culling enabled: " + culling);
        
        // Check limits
        int[] maxTextureSize = new int[1];
        glGetIntegerv(GL_MAX_TEXTURE_SIZE, maxTextureSize);
        Logger.info(CATEGORY, "Max texture size: " + maxTextureSize[0]);
        
        int[] maxVertexAttribs = new int[1];
        glGetIntegerv(GL_MAX_VERTEX_ATTRIBS, maxVertexAttribs);
        Logger.info(CATEGORY, "Max vertex attributes: " + maxVertexAttribs[0]);
        
        checkGLError("OpenGL capabilities check");
    }
    
    /**
     * Verify rendering state before drawing.
     */
    public static boolean verifyRenderState() {
        boolean allGood = true;
        
        // Check if we have a valid shader bound
        int[] currentProgram = new int[1];
        glGetIntegerv(GL_CURRENT_PROGRAM, currentProgram);
        if (currentProgram[0] == 0) {
            Logger.warn(CATEGORY, "No shader program bound!");
            allGood = false;
        }
        
        // Check if we have a valid VAO bound
        int[] currentVAO = new int[1];
        glGetIntegerv(GL_VERTEX_ARRAY_BINDING, currentVAO);
        if (currentVAO[0] == 0) {
            Logger.warn(CATEGORY, "No VAO bound!");
            allGood = false;
        }
        
        // Check if depth test is enabled
        if (!glIsEnabled(GL_DEPTH_TEST)) {
            Logger.warn(CATEGORY, "Depth test is disabled!");
            allGood = false;
        }
        
        checkGLError("Render state verification");
        return allGood;
    }
    
    /**
     * Comprehensive startup health check.
     */
    public static boolean performStartupCheck() {
        Logger.info(CATEGORY, "=== Performing Startup Health Check ===");
        boolean allPassed = true;
        
        // Check OpenGL
        checkOpenGLCapabilities();
        
        // Check for any existing errors
        if (!checkGLError("Startup check")) {
            allPassed = false;
        }
        
        Logger.info(CATEGORY, "Startup health check: " + (allPassed ? "✅ PASSED" : "❌ FAILED"));
        return allPassed;
    }
}

