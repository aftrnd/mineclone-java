package com.mineclone.game;

import org.joml.Matrix4f;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;

/**
 * HandRenderer - Renders the player's hand/arm in first-person view.
 * Based EXACTLY on net.minecraft.client.renderer.ItemInHandRenderer
 * Uses Minecraft's player model with Steve skin texture and proper UV mapping.
 */
public class HandRenderer {
    
    private int vao;
    private int vbo;
    private int vertexCount;
    private ShaderProgram shader;
    private SteveTexture steveTexture;
    
    // Minecraft arm dimensions (4x12x4 pixels - Minecraft's exact)
    private static final float ARM_WIDTH = 4.0f / 16.0f;
    private static final float ARM_HEIGHT = 12.0f / 16.0f;
    private static final float ARM_DEPTH = 4.0f / 16.0f;
    
    // Swing animation state (Minecraft's attackAnim system)
    private float swingProgress = 0.0f;
    private float swingProgressO = 0.0f;  // Previous for interpolation
    private int swingTime = 0;
    private static final int SWING_DURATION = 6;  // Minecraft's exact: 6 ticks
    
    /**
     * Initialize hand renderer.
     */
    public void init() throws Exception {
        // Load Steve skin texture
        steveTexture = new SteveTexture();
        steveTexture.load();
        
        // Load shader
        shader = new ShaderProgram();
        shader.createVertexShader(loadResource("/shaders/hand.vsh"));
        shader.createFragmentShader(loadResource("/shaders/hand.fsh"));
        shader.link();
        
        // Create uniforms
        shader.createUniform("projectionMatrix");
        shader.createUniform("textureSampler");
         
        createHandMesh();
    }
    
    /**
     * Load shader source from resources.
     */
    private String loadResource(String path) throws Exception {
        try (java.io.InputStream in = getClass().getResourceAsStream(path)) {
            if (in == null) {
                throw new Exception("Could not load resource: " + path);
            }
            return new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
    }
    
    /**
     * Create arm mesh with Minecraft's exact UV mapping.
     * Steve skin is 64x64, right arm UVs are at (40,16) to (56,32).
     */
    private void createHandMesh() {
        // Minecraft's EXACT right arm box: addBox(-3.0F, -2.0F, -2.0F, 4.0F, 12.0F, 4.0F)
        // This means: X from -3 to 1, Y from -2 to 10, Z from -2 to 2 (in 1/16 scale)
        // NOT centered on X-axis!
        float x0 = -3.0f / 16.0f;  // Left edge: -0.1875
        float x1 = 1.0f / 16.0f;   // Right edge: 0.0625
        float y0 = -2.0f / 16.0f;  // Bottom: -0.125
        float y1 = 10.0f / 16.0f;  // Top: 0.625
        float z0 = -2.0f / 16.0f;  // Back: -0.125
        float z1 = 2.0f / 16.0f;   // Front: 0.125
        
        // Minecraft's RIGHT arm UV coordinates (on 64x64 texture) - FROM SOURCE CODE
        // HumanoidModel.java line 69: texOffs(40, 16).addBox(-3.0F, -2.0F, -2.0F, 4.0F, 12.0F, 4.0F)
        // Right arm UV starts at (40,16), dimensions: 4 pixels wide, 12 pixels tall, 4 pixels deep
        float u0 = 40.0f / 64.0f;  // Left edge
        float u1 = 44.0f / 64.0f;  // Front/Back edge
        float u2 = 48.0f / 64.0f;  // Right edge  
        float u3 = 52.0f / 64.0f;  // Far edge
        float u4 = 56.0f / 64.0f;  // End edge
        float v0 = 16.0f / 64.0f;  // Top cap start
        float v1 = 20.0f / 64.0f;  // Main texture start
        float v2 = 32.0f / 64.0f;  // Main texture end
        
        // Vertex format: position (3) + texCoord (2) + normal (3) = 8 floats per vertex
        // Using Minecraft's EXACT box coordinates (not centered!)
        float[] vertices = {
            // Front face (+Z) - Normal: (0, 0, 1)
            x0, y0, z1,  u1, v2,  0, 0, 1,
            x1, y0, z1,  u2, v2,  0, 0, 1,
            x1, y1, z1,  u2, v1,  0, 0, 1,
            x0, y1, z1,  u1, v1,  0, 0, 1,
            
            // Back face (-Z) - Normal: (0, 0, -1)
            x0, y0, z0,  u4, v2,  0, 0, -1,
            x1, y0, z0,  u3, v2,  0, 0, -1,
            x1, y1, z0,  u3, v1,  0, 0, -1,
            x0, y1, z0,  u4, v1,  0, 0, -1,
            
            // Left face (-X) - Normal: (-1, 0, 0)
            x0, y0, z0,  u1, v2,  -1, 0, 0,
            x0, y0, z1,  u0, v2,  -1, 0, 0,
            x0, y1, z1,  u0, v1,  -1, 0, 0,
            x0, y1, z0,  u1, v1,  -1, 0, 0,
            
            // Right face (+X) - Normal: (1, 0, 0)
            x1, y0, z0,  u3, v2,  1, 0, 0,
            x1, y0, z1,  u2, v2,  1, 0, 0,
            x1, y1, z1,  u2, v1,  1, 0, 0,
            x1, y1, z0,  u3, v1,  1, 0, 0,
            
            // Top face (+Y) - Normal: (0, 1, 0)
            x0, y1, z0,  u1, v0,  0, 1, 0,
            x0, y1, z1,  u1, v1,  0, 1, 0,
            x1, y1, z1,  u2, v1,  0, 1, 0,
            x1, y1, z0,  u2, v0,  0, 1, 0,
            
            // Bottom face (-Y) - Normal: (0, -1, 0)
            x0, y0, z0,  u2, v0,  0, -1, 0,
            x0, y0, z1,  u2, v1,  0, -1, 0,
            x1, y0, z1,  u3, v1,  0, -1, 0,
            x1, y0, z0,  u3, v0,  0, -1, 0,
        };
        
        int[] indices = {
            0, 1, 2,  0, 2, 3,    // Front
            5, 4, 7,  5, 7, 6,    // Back
            8, 9, 10,  8, 10, 11, // Left
            13, 12, 15,  13, 15, 14, // Right
            16, 17, 18,  16, 18, 19, // Top
            20, 21, 22,  20, 22, 23  // Bottom
        };
        
        vertexCount = indices.length;
        
        vao = glGenVertexArrays();
        glBindVertexArray(vao);
        
        vbo = glGenBuffers();
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        glBufferData(GL_ARRAY_BUFFER, vertices, GL_STATIC_DRAW);
        
        int ebo = glGenBuffers();
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, ebo);
        glBufferData(GL_ELEMENT_ARRAY_BUFFER, indices, GL_STATIC_DRAW);
        
        // VERTEX FORMAT: position (3) + texCoord (2) + normal (3) = 8 floats per vertex
        int stride = 8 * Float.BYTES;
        
        // Position attribute (location = 0)
        glVertexAttribPointer(0, 3, GL_FLOAT, false, stride, 0);
        glEnableVertexAttribArray(0);
        
        // TexCoord attribute (location = 1)
        glVertexAttribPointer(1, 2, GL_FLOAT, false, stride, 3 * Float.BYTES);
        glEnableVertexAttribArray(1);
        
        // Normal attribute (location = 2)
        glVertexAttribPointer(2, 3, GL_FLOAT, false, stride, 5 * Float.BYTES);
        glEnableVertexAttribArray(2);
        
        glBindVertexArray(0);
    }
    
    /**
     * Start swing animation (Minecraft's EXACT swing system).
     * Minecraft starts at swingTime=-1, then increments each tick.
     */
    public void startSwing() {
        // Allow swing to restart (for continuous breaking) - Minecraft does this too!
        swingTime = -1;  // Minecraft starts at -1!
        swingProgress = -1.0f / (float) SWING_DURATION;  // Start at negative
        swingProgressO = swingProgress;  // Initialize old value
        
        // Debug: Log swing start occasionally
        if (Math.random() < 0.1) {
            System.out.println("HandRenderer: Starting swing (duration=" + SWING_DURATION + " ticks)");
        }
    }
    
    /**
     * Update swing animation (called each tick, not each frame!).
     * Minecraft's EXACT LivingEntity.tickSwingAnimation() - INCREMENTS, not decrements!
     */
    public void tick() {
        swingProgressO = swingProgress;  // Store old value
        
        if (swingTime >= -1 && swingTime < SWING_DURATION) {
            swingTime++;  // Minecraft INCREMENTS: -1 -> 0 -> 1 -> ... -> 6
            // Minecraft's exact formula: attackAnim = swingTime / duration
            swingProgress = (float) swingTime / (float) SWING_DURATION;
        } else {
            swingTime = SWING_DURATION;  // Stop at duration
            swingProgress = 1.0f;
        }
    }
    
    /**
     * Get interpolated swing progress.
     */
    private float getSwingProgress(float partialTick) {
        return swingProgressO + (swingProgress - swingProgressO) * partialTick;
    }
    
    /**
     * Render the player's arm - MINECRAFT'S EXACT rendering pipeline.
     * 
     * Based on:
     * - GameRenderer.renderItemInHand() line 464: inverts view matrix
     * - ItemInHandRenderer.renderHandsWithItems() lines 327-328: camera bobbing
     * - ItemInHandRenderer.renderPlayerArm() lines 233-260: arm transformations
     * 
     * @param projection The projection matrix
     * @param viewMatrix The camera view matrix
     * @param partialTick Interpolation factor
     * @param player The local player
     */
    public void render(Matrix4f projection, Matrix4f viewMatrix, float partialTick, LocalPlayer player) {
        shader.bind();
        
        // === STEP 1: Setup pose stack ===
        // Start with identity matrix (render in view space directly)
        Matrix4f poseStack = new Matrix4f().identity();
        
        // === STEP 2: Arm positioning (ItemInHandRenderer.renderPlayerArm lines 233-260) ===
        float inverseArmHeight = 0.0f;  // equipProgress in Minecraft
        float attackValue = getSwingProgress(partialTick);  // Minecraft's attack animation
        boolean isRightArm = true;  // HumanoidArm.RIGHT
        
        // Minecraft's exact calculations
        float invert = isRightArm ? 1.0f : -1.0f;
        float sqrtAttackValue = (float) Math.sqrt(attackValue);
        float xSwingPosition = -0.3f * (float) Math.sin(sqrtAttackValue * Math.PI);
        float ySwingPosition = 0.4f * (float) Math.sin(sqrtAttackValue * 2.0 * Math.PI);
        float zSwingPosition = -0.4f * (float) Math.sin(attackValue * Math.PI);
        
        // Debug animation
        if (attackValue > 0) {
            System.out.println("Swing: attackValue=" + attackValue + " sqrt=" + sqrtAttackValue + 
                             " xPos=" + xSwingPosition + " yPos=" + ySwingPosition + " zPos=" + zSwingPosition);
        }
        
        // Line 240: Initial positioning
        poseStack.translate(
            invert * (xSwingPosition + 0.64000005f), 
            ySwingPosition + -0.6f + inverseArmHeight * -0.6f, 
            zSwingPosition + -0.71999997f
        );
        
        // Line 241: Base rotation
        poseStack.rotateY((float) Math.toRadians(invert * 45.0f));
        
        // Lines 242-245: Swing rotations
        float zSwingRotation = (float) Math.sin(attackValue * attackValue * Math.PI);
        float ySwingRotation = (float) Math.sin(sqrtAttackValue * Math.PI);
        poseStack.rotateY((float) Math.toRadians(invert * ySwingRotation * 70.0f));
        poseStack.rotateZ((float) Math.toRadians(invert * zSwingRotation * -20.0f));
        
        // Lines 247-251: Player model positioning
        poseStack.translate(invert * -1.0f, 3.6f, 3.5f);
        poseStack.rotateZ((float) Math.toRadians(invert * 120.0f));
        poseStack.rotateX((float) Math.toRadians(200.0f));
        poseStack.rotateY((float) Math.toRadians(invert * -135.0f));
        poseStack.translate(invert * 5.6f, 0.0f, 0.0f);
        
        // Lines 252-258: In Minecraft, this calls avatarRenderer.renderRightHand()
        // which renders the actual player model arm geometry
        // We render our simple arm mesh instead
        // NO CUSTOM ADJUSTMENTS - ONLY MINECRAFT'S CODE!
        
        // Combine with projection matrix
        Matrix4f mvp = new Matrix4f(projection).mul(poseStack);
        
        shader.setUniform("projectionMatrix", mvp);
        shader.setUniform("textureSampler", 0);
        
        // Bind Steve skin texture
        steveTexture.bind();
        
        // Disable face culling for hand (so both sides are visible)
        boolean wasCulling = glIsEnabled(org.lwjgl.opengl.GL11.GL_CULL_FACE);
        if (wasCulling) glDisable(org.lwjgl.opengl.GL11.GL_CULL_FACE);
        
        glBindVertexArray(vao);
        glDrawElements(GL_TRIANGLES, vertexCount, GL_UNSIGNED_INT, 0);
        glBindVertexArray(0);
        
        // Restore face culling state
        if (wasCulling) glEnable(org.lwjgl.opengl.GL11.GL_CULL_FACE);
        
        steveTexture.unbind();
        shader.unbind();
    }
    
    /**
     * Clean up resources.
     */
    public void cleanup() {
        if (shader != null) {
            shader.cleanup();
        }
        if (steveTexture != null) {
            steveTexture.cleanup();
        }
        glDeleteVertexArrays(vao);
        glDeleteBuffers(vbo);
    }
}
