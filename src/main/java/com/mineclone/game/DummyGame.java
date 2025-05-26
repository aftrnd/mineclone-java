package com.mineclone.game;

import com.mineclone.core.engine.IGameLogic;
import com.mineclone.core.engine.Window;
import com.mineclone.core.input.MouseInput;
import com.mineclone.core.math.Matrix4f;
import com.mineclone.core.math.Vector3f;
import com.mineclone.core.render.Camera;
import com.mineclone.core.render.ShaderProgram;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryUtil;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.FloatBuffer;
import java.util.stream.Collectors;
import java.nio.IntBuffer;

public class DummyGame implements IGameLogic {
    private int vaoId;
    private int vboId;
    private ShaderProgram shader;

    @Override
    public void init(Window window) throws Exception {
        System.out.println("Initializing game...");
        
        // Create and bind the VAO
        vaoId = GL30.glGenVertexArrays();
        GL30.glBindVertexArray(vaoId);
        System.out.println("Created VAO: " + vaoId);

        // Create the vertices for a simple triangle
        float[] vertices = {
            // Position (x,y)    Color (r,g,b)
             0.0f,  0.25f,   1.0f, 0.0f, 0.0f,  // Top (red)
            -0.25f, -0.25f,   0.0f, 1.0f, 0.0f,  // Bottom-left (green)
             0.25f, -0.25f,   0.0f, 0.0f, 1.0f   // Bottom-right (blue)
        };

        // Print vertex data for debugging
        System.out.println("Vertex data:");
        for (int i = 0; i < vertices.length; i += 5) {
            System.out.printf("Vertex %d: pos(%.2f, %.2f) color(%.2f, %.2f, %.2f)%n",
                i/5, vertices[i], vertices[i+1], vertices[i+2], vertices[i+3], vertices[i+4]);
        }

        // Create and bind the VBO
        vboId = GL15.glGenBuffers();
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vboId);
        System.out.println("Created VBO: " + vboId);

        // Upload the vertex data
        FloatBuffer buffer = MemoryUtil.memAllocFloat(vertices.length);
        buffer.put(vertices).flip();
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, buffer, GL15.GL_STATIC_DRAW);
        MemoryUtil.memFree(buffer);
        System.out.println("Uploaded vertex data");

        // Set the vertex attribute pointers
        // Position attribute (2 floats)
        GL20.glVertexAttribPointer(0, 2, GL11.GL_FLOAT, false, 5 * Float.BYTES, 0);
        GL20.glEnableVertexAttribArray(0);
        System.out.println("Position attribute setup: location=0, size=2, stride=" + (5 * Float.BYTES) + ", offset=0");

        // Color attribute (3 floats)
        GL20.glVertexAttribPointer(1, 3, GL11.GL_FLOAT, false, 5 * Float.BYTES, 2 * Float.BYTES);
        GL20.glEnableVertexAttribArray(1);
        System.out.println("Color attribute setup: location=1, size=3, stride=" + (5 * Float.BYTES) + ", offset=" + (2 * Float.BYTES));

        // Create and setup shader
        shader = new ShaderProgram();
        String vertexShaderCode = loadResource("/shaders/vertex.glsl");
        String fragmentShaderCode = loadResource("/shaders/fragment.glsl");
        System.out.println("Loaded vertex shader:\n" + vertexShaderCode);
        System.out.println("Loaded fragment shader:\n" + fragmentShaderCode);
        
        try {
            shader.createVertexShader(vertexShaderCode);
            shader.createFragmentShader(fragmentShaderCode);
            shader.link();
            System.out.println("Created and linked shader program");

            // Validate shader program
            shader.validate();
            System.out.println("Shader validation complete");
        } catch (Exception e) {
            System.err.println("Error creating shader program: " + e.getMessage());
            e.printStackTrace();
            throw e;
        }

        // Unbind the VBO and VAO
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        GL30.glBindVertexArray(0);
        System.out.println("Initialization complete");
    }

    private String loadResource(String fileName) throws Exception {
        try (InputStream in = getClass().getResourceAsStream(fileName)) {
            if (in == null) {
                throw new Exception("Could not find resource: " + fileName);
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(in))) {
                String code = reader.lines().collect(Collectors.joining("\n"));
                if (code.isEmpty()) {
                    throw new Exception("Shader file is empty: " + fileName);
                }
                return code;
            }
        }
    }

    @Override
    public void input(Window window, MouseInput mouseInput) {
        // No input handling needed for the simple triangle
    }

    @Override
    public void update(float interval, MouseInput mouseInput) {
        // No updates needed for the simple triangle
    }

    @Override
    public void render(Window window) {
        if (window.isResized()) {
            GL11.glViewport(0, 0, window.getWidth(), window.getHeight());
            window.setResized(false);
        }

        // Clear the screen with black
        GL11.glClearColor(0.0f, 0.0f, 0.0f, 0.0f);
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);

        // Use the shader
        shader.bind();

        // Bind the VAO
        GL30.glBindVertexArray(vaoId);

        // Draw the triangle
        GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 3);

        // Check for OpenGL errors
        int error = GL11.glGetError();
        if (error != GL11.GL_NO_ERROR) {
            System.err.println("OpenGL error after draw: " + error);
        }

        // Unbind the VAO
        GL30.glBindVertexArray(0);

        // Unbind the shader
        shader.unbind();
    }

    @Override
    public void cleanup() {
        // Cleanup resources
        shader.cleanup();
        GL15.glDeleteBuffers(vboId);
        GL30.glDeleteVertexArrays(vaoId);
    }
} 