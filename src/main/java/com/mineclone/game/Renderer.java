package com.mineclone.game;

import com.mineclone.core.ResourceLoader;
import com.mineclone.core.utils.Logger;
import com.mineclone.core.utils.RenderHealthCheck;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;

/**
 * Modern OpenGL renderer for Minecraft clone using shaders and textures
 */
public class Renderer {
    private ShaderProgram shaderProgram;
    private int vaoId;
    private int vboId;
    private int vertexCount;
    private TextureAtlas textureAtlas;

    public Renderer() throws Exception {
        Logger.info("Renderer", "Initializing renderer...");
        
        // Create shader program
        shaderProgram = new ShaderProgram();
        shaderProgram.createVertexShader(ResourceLoader.loadResource("/shaders/vertex.glsl"));
        shaderProgram.createFragmentShader(ResourceLoader.loadResource("/shaders/fragment.glsl"));
        shaderProgram.link();
        
        // Validate shader program
        if (!RenderHealthCheck.validateShaderProgram(shaderProgram.getProgramId(), "Block Shader")) {
            throw new RuntimeException("Shader program validation failed!");
        }

        // Create uniforms
        shaderProgram.createUniform("projection");
        shaderProgram.createUniform("view");
        shaderProgram.createUniform("model");
        shaderProgram.createUniform("blockTexture");  // Texture sampler uniform
        
        // Minecraft-style fog uniforms
        shaderProgram.createUniform("fogColor");
        shaderProgram.createUniform("fogStart");
        shaderProgram.createUniform("fogEnd");
        
        RenderHealthCheck.checkGLError("Shader uniform creation");

        // Create VAO and VBO
        vaoId = glGenVertexArrays();
        vboId = glGenBuffers();
        
        // Validate VAO and VBO
        if (!RenderHealthCheck.validateVAO(vaoId, "Renderer VAO")) {
            throw new RuntimeException("VAO creation failed!");
        }
        if (!RenderHealthCheck.validateVBO(vboId, "Renderer VBO")) {
            throw new RuntimeException("VBO creation failed!");
        }
        
        Logger.info("Renderer", "Renderer initialized (VAO: " + vaoId + ", VBO: " + vboId + ")");
    }
    
    /**
     * Set the texture atlas to use for rendering.
     */
    public void setTextureAtlas(TextureAtlas atlas) {
        this.textureAtlas = atlas;
    }

    public void render(Camera camera, Chunk chunk, Matrix4f projectionMatrix) {
        // Enable depth testing and face culling
        glEnable(GL_DEPTH_TEST);
        glDepthFunc(GL_LESS);
        glEnable(GL_CULL_FACE);
        glCullFace(GL_BACK);
        
        // Enable alpha blending for transparent blocks (leaves, glass)
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

        shaderProgram.bind();
        
        // Bind texture atlas
        if (textureAtlas != null) {
            glActiveTexture(GL_TEXTURE0);
            textureAtlas.bind();
            shaderProgram.setUniform("blockTexture", 0);  // Texture unit 0
        }

        // Set projection matrix
        shaderProgram.setUniform("projection", projectionMatrix);

        // Set view matrix (from camera)
        Matrix4f viewMatrix = camera.getViewMatrix();
        shaderProgram.setUniform("view", viewMatrix);
        
        // Set Minecraft-style fog parameters
        // Sky blue color (matches our clear color) with full intensity
        org.joml.Vector4f fogColor = new org.joml.Vector4f(0.53f, 0.81f, 0.92f, 1.0f);
        
        // Calculate fog distances based on chunk render distance
        // Minecraft typically starts fog at ~80% of render distance
        float renderDistance = 16.0f * 12.0f;  // 12 chunks * 16 blocks = 192 blocks
        float fogStart = renderDistance * 0.75f;  // Start fog at 75%
        float fogEnd = renderDistance * 0.95f;   // Full fog at 95%
        
        shaderProgram.setUniform("fogColor", fogColor);
        shaderProgram.setUniform("fogStart", fogStart);
        shaderProgram.setUniform("fogEnd", fogEnd);

        // Set model matrix - translate to chunk's world position
        ChunkPos pos = chunk.getPos();
        Matrix4f modelMatrix = new Matrix4f().identity()
            .translate(pos.getWorldX(), 0, pos.getWorldZ());
        shaderProgram.setUniform("model", modelMatrix);

        // Minecraft-style async: Check if mesh data is ready from background thread
        // IMPORTANT: Only upload if mesh generation is complete (not currently generating)
        float[] pendingMesh = chunk.getPendingMeshData();
        if (pendingMesh != null && !chunk.isMeshGenerating()) {
            // Mesh was generated on background thread - upload to GPU NOW (on main thread)
            int newVertexCount = pendingMesh.length / 8;
            
            // Create VBO if needed
            if (chunk.getVboId() == -1) {
                int chunkVbo = glGenBuffers();
                chunk.setVboId(chunkVbo);
                Logger.debug("Renderer", "Created VBO " + chunkVbo + " for chunk " + pos);
            }
            
            if (newVertexCount > 0) {
                // Validate mesh data before upload
                boolean hasInvalidData = false;
                for (int i = 0; i < pendingMesh.length && i < 100; i++) {  // Check first 100 values
                    if (Float.isNaN(pendingMesh[i]) || Float.isInfinite(pendingMesh[i])) {
                        Logger.error("Renderer", "Invalid mesh data at index " + i + " for chunk " + pos + ": " + pendingMesh[i]);
                        hasInvalidData = true;
                        break;
                    }
                }
                
                if (hasInvalidData || pendingMesh.length == 0 || pendingMesh.length > 10000000) {
                    Logger.error("Renderer", "Skipping VBO upload for chunk " + pos + " - invalid data (length=" + pendingMesh.length + ", hasInvalidData=" + hasInvalidData + ")");
                } else {
                    glBindBuffer(GL_ARRAY_BUFFER, chunk.getVboId());
                    FloatBuffer vertexBuffer = org.lwjgl.BufferUtils.createFloatBuffer(pendingMesh.length);
                    vertexBuffer.put(pendingMesh).flip();
                    glBufferData(GL_ARRAY_BUFFER, vertexBuffer, GL_STATIC_DRAW);
                    glBindBuffer(GL_ARRAY_BUFFER, 0);
                    
                    RenderHealthCheck.checkGLError("VBO upload for chunk " + pos);
                }
            }
            
            chunk.setVertexCount(newVertexCount);
            chunk.clearPendingMeshData();  // Clear so we don't re-upload
            chunk.setVboUpdated();
        }
        
        
        // Draw using cached VBO (happens every frame, no reuploading!)
        int chunkVertexCount = chunk.getVertexCount();
        if (chunkVertexCount > 0 && chunk.getVboId() != -1) {
            // Actually rendering this chunk
            glBindVertexArray(vaoId);
            glBindBuffer(GL_ARRAY_BUFFER, chunk.getVboId());  // Use chunk's VBO!

            int stride = 8 * Float.BYTES;
            glVertexAttribPointer(0, 3, GL_FLOAT, false, stride, 0);
            glEnableVertexAttribArray(0);
            glVertexAttribPointer(1, 3, GL_FLOAT, false, stride, 3 * Float.BYTES);
            glEnableVertexAttribArray(1);
            glVertexAttribPointer(2, 2, GL_FLOAT, false, stride, 6 * Float.BYTES);
            glEnableVertexAttribArray(2);

            glDrawArrays(GL_TRIANGLES, 0, chunkVertexCount);

            glDisableVertexAttribArray(0);
            glDisableVertexAttribArray(1);
            glDisableVertexAttribArray(2);
            glBindBuffer(GL_ARRAY_BUFFER, 0);
            glBindVertexArray(0);
        }

        shaderProgram.unbind();
    }

    public void cleanup() {
        Logger.info("Renderer", "Cleaning up renderer resources...");
        shaderProgram.cleanup();
        if (vboId != 0) {
            glDeleteBuffers(vboId);
            Logger.debug("Renderer", "Deleted VBO: " + vboId);
        }
        if (vaoId != 0) {
            glDeleteVertexArrays(vaoId);
            Logger.debug("Renderer", "Deleted VAO: " + vaoId);
        }
        Logger.info("Renderer", "Renderer cleanup complete");
    }
}