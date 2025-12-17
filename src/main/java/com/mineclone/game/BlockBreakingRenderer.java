package com.mineclone.game;

import org.joml.Matrix4f;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL13.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;

/**
 * Renders block breaking animation overlay (Minecraft's cracking texture).
 * Displays destroy_stage_0.png through destroy_stage_9.png on the block being broken.
 * Uses modern OpenGL (VBO + shaders) instead of deprecated immediate mode.
 */
public class BlockBreakingRenderer {
    private int[] breakingTextures = new int[10];  // 10 breaking stages
    private boolean initialized = false;
    
    // VBO/VAO for cube overlay
    private int vao;
    private int vbo;
    private int vertexCount;
    private ShaderProgram shader;
    
    /**
     * Load the 10 breaking stage textures and create cube mesh.
     */
    public void init() throws Exception {
        // Load breaking textures
        for (int i = 0; i < 10; i++) {
            String path = "/textures/block/destroy_stage_" + i + ".png";
            breakingTextures[i] = loadTexture(path);
        }
        
        // Create shader (reuse block shader for simplicity)
        shader = new ShaderProgram();
        shader.createVertexShader(loadShaderResource("/shaders/vertex.glsl"));
        shader.createFragmentShader(loadShaderResource("/shaders/fragment.glsl"));
        shader.link();
        
        shader.createUniform("projection");
        shader.createUniform("view");
        shader.createUniform("model");
        shader.createUniform("blockTexture");
        shader.createUniform("fogColor");
        shader.createUniform("fogStart");
        shader.createUniform("fogEnd");
        
        // Create cube overlay mesh
        createCubeMesh();
        
        initialized = true;
        System.out.println("✓ Loaded 10 block breaking textures + shader");
    }
    
    /**
     * Create a unit cube mesh for the breaking overlay.
     */
    private void createCubeMesh() {
        // Cube vertices with texture coordinates
        // Format: position (3 floats) + color (3 floats) + texCoord (2 floats) = 8 floats per vertex
        float[] vertices = createCubeVertices();
        
        vertexCount = vertices.length / 8;
        
        // Create VAO
        vao = glGenVertexArrays();
        glBindVertexArray(vao);
        
        // Create VBO
        vbo = glGenBuffers();
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        glBufferData(GL_ARRAY_BUFFER, vertices, GL_STATIC_DRAW);
        
        // Position attribute (location = 0)
        glVertexAttribPointer(0, 3, GL_FLOAT, false, 8 * Float.BYTES, 0);
        glEnableVertexAttribArray(0);
        
        // Color attribute (location = 1)
        glVertexAttribPointer(1, 3, GL_FLOAT, false, 8 * Float.BYTES, 3 * Float.BYTES);
        glEnableVertexAttribArray(1);
        
        // Texture coordinate attribute (location = 2)
        glVertexAttribPointer(2, 2, GL_FLOAT, false, 8 * Float.BYTES, 6 * Float.BYTES);
        glEnableVertexAttribArray(2);
        
        glBindVertexArray(0);
    }
    
    /**
     * Load shader from resources.
     */
    private String loadShaderResource(String path) throws Exception {
        try (java.io.InputStream in = getClass().getResourceAsStream(path)) {
            if (in == null) {
                throw new Exception("Could not load shader: " + path);
            }
            return new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
    }
    
    /**
     * Load a texture from resources.
     */
    private int loadTexture(String path) throws Exception {
        try (java.io.InputStream in = getClass().getResourceAsStream(path)) {
            if (in == null) {
                throw new Exception("Could not load texture: " + path);
            }
            
            // Use LWJGL's STB image loader
            org.lwjgl.system.MemoryStack stack = org.lwjgl.system.MemoryStack.stackPush();
            java.nio.IntBuffer width = stack.mallocInt(1);
            java.nio.IntBuffer height = stack.mallocInt(1);
            java.nio.IntBuffer channels = stack.mallocInt(1);
            
            byte[] bytes = in.readAllBytes();
            java.nio.ByteBuffer buffer = org.lwjgl.BufferUtils.createByteBuffer(bytes.length);
            buffer.put(bytes).flip();
            
            java.nio.ByteBuffer image = org.lwjgl.stb.STBImage.stbi_load_from_memory(
                buffer, width, height, channels, 4);
            
            if (image == null) {
                throw new Exception("Failed to load texture: " + path);
            }
            
            int textureId = glGenTextures();
            glBindTexture(GL_TEXTURE_2D, textureId);
            
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
            
            glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, width.get(0), height.get(0),
                0, GL_RGBA, GL_UNSIGNED_BYTE, image);
            
            org.lwjgl.stb.STBImage.stbi_image_free(image);
            stack.pop();
            
            return textureId;
        }
    }
    
    /**
     * Create vertices for a 1x1x1 cube at origin.
     * Format: position (3) + color (3) + texCoord (2) = 8 floats per vertex
     */
    private float[] createCubeVertices() {
        // White color for all vertices (texture will show through)
        float r = 1.0f, g = 1.0f, b = 1.0f;
        
        return new float[] {
            // Front face (+Z) - 2 triangles = 6 vertices
            0, 0, 1,  r, g, b,  0, 1,
            1, 0, 1,  r, g, b,  1, 1,
            1, 1, 1,  r, g, b,  1, 0,
            1, 1, 1,  r, g, b,  1, 0,
            0, 1, 1,  r, g, b,  0, 0,
            0, 0, 1,  r, g, b,  0, 1,
            
            // Back face (-Z)
            1, 0, 0,  r, g, b,  0, 1,
            0, 0, 0,  r, g, b,  1, 1,
            0, 1, 0,  r, g, b,  1, 0,
            0, 1, 0,  r, g, b,  1, 0,
            1, 1, 0,  r, g, b,  0, 0,
            1, 0, 0,  r, g, b,  0, 1,
            
            // Top face (+Y)
            0, 1, 0,  r, g, b,  0, 0,
            0, 1, 1,  r, g, b,  0, 1,
            1, 1, 1,  r, g, b,  1, 1,
            1, 1, 1,  r, g, b,  1, 1,
            1, 1, 0,  r, g, b,  1, 0,
            0, 1, 0,  r, g, b,  0, 0,
            
            // Bottom face (-Y)
            0, 0, 0,  r, g, b,  0, 1,
            1, 0, 0,  r, g, b,  1, 1,
            1, 0, 1,  r, g, b,  1, 0,
            1, 0, 1,  r, g, b,  1, 0,
            0, 0, 1,  r, g, b,  0, 0,
            0, 0, 0,  r, g, b,  0, 1,
            
            // Right face (+X)
            1, 0, 0,  r, g, b,  0, 1,
            1, 1, 0,  r, g, b,  0, 0,
            1, 1, 1,  r, g, b,  1, 0,
            1, 1, 1,  r, g, b,  1, 0,
            1, 0, 1,  r, g, b,  1, 1,
            1, 0, 0,  r, g, b,  0, 1,
            
            // Left face (-X)
            0, 0, 1,  r, g, b,  0, 1,
            0, 1, 1,  r, g, b,  0, 0,
            0, 1, 0,  r, g, b,  1, 0,
            0, 1, 0,  r, g, b,  1, 0,
            0, 0, 0,  r, g, b,  1, 1,
            0, 0, 1,  r, g, b,  0, 1
        };
    }
    
    /**
     * Render breaking overlay on a block using modern OpenGL.
     */
    public void renderBreakingBlock(int blockX, int blockY, int blockZ, int breakingStage,
                                    Matrix4f projectionMatrix, Matrix4f viewMatrix) {
        if (!initialized || breakingStage < 0 || breakingStage > 9) {
            System.out.println("⚠️ Breaking animation NOT rendered: initialized=" + initialized + ", stage=" + breakingStage);
            return;
        }
        
        System.out.println("🔨 Rendering breaking animation at (" + blockX + "," + blockY + "," + blockZ + ") stage=" + breakingStage);
        
        // Enable blending for overlay (Minecraft style)
        glEnable(GL_BLEND);
        glBlendFunc(GL_DST_COLOR, GL_SRC_COLOR); // Multiply blend mode like Minecraft
        
        // Polygon offset to prevent z-fighting (render slightly in front)
        glEnable(GL_POLYGON_OFFSET_FILL);
        glPolygonOffset(-1.0f, -1.0f);
        
        // Disable depth writing so overlay doesn't occlude other blocks
        glDepthMask(false);
        
        // Disable face culling so we see all faces of the overlay
        glDisable(GL_CULL_FACE);
        
        // Use shader
        shader.bind();
        
        // Set uniforms
        shader.setUniform("blockTexture", 0);
        shader.setUniform("fogColor", new org.joml.Vector4f(0.6f, 0.7f, 0.9f, 0.0f)); // Sky color, no fog for overlay
        shader.setUniform("fogStart", 1000.0f);
        shader.setUniform("fogEnd", 2000.0f);
        
        // Create model matrix (translate to block position)
        Matrix4f modelMatrix = new Matrix4f().identity()
            .translate(blockX, blockY, blockZ)
            .scale(1.01f);  // Slightly larger than block to prevent z-fighting
        
        shader.setUniform("model", modelMatrix);
        shader.setUniform("view", viewMatrix);
        shader.setUniform("projection", projectionMatrix);
        
        // Bind breaking texture
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, breakingTextures[breakingStage]);
        
        // Draw cube
        glBindVertexArray(vao);
        glDrawArrays(GL_TRIANGLES, 0, vertexCount);
        glBindVertexArray(0);
        
        // Restore state
        shader.unbind();
        glDepthMask(true); // Re-enable depth writing
        glEnable(GL_CULL_FACE);
        glDisable(GL_POLYGON_OFFSET_FILL);
        glDisable(GL_BLEND);
    }
    
    /**
     * Cleanup textures, shader, and VBO/VAO.
     */
    public void cleanup() {
        if (shader != null) {
            shader.cleanup();
        }
        if (vbo != 0) {
            glDeleteBuffers(vbo);
        }
        if (vao != 0) {
            glDeleteVertexArrays(vao);
        }
        for (int texture : breakingTextures) {
            if (texture != 0) {
                glDeleteTextures(texture);
            }
        }
    }
}

