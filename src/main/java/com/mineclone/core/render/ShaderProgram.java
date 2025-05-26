package com.mineclone.core.render;

import com.mineclone.core.math.Matrix4f;
import org.lwjgl.system.MemoryStack;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.HashMap;
import java.util.Map;

import static org.lwjgl.opengl.GL20.*;

public class ShaderProgram {
    private final int programId;
    private int vertexShaderId;
    private int fragmentShaderId;
    private final Map<String, Integer> uniforms;

    public ShaderProgram() throws Exception {
        programId = glCreateProgram();
        if (programId == 0) {
            throw new Exception("Could not create Shader");
        }
        uniforms = new HashMap<>();
    }

    public void createVertexShader(String shaderCode) throws Exception {
        vertexShaderId = createShader(shaderCode, GL_VERTEX_SHADER);
    }

    public void createFragmentShader(String shaderCode) throws Exception {
        fragmentShaderId = createShader(shaderCode, GL_FRAGMENT_SHADER);
    }

    protected int createShader(String shaderCode, int shaderType) throws Exception {
        int shaderId = glCreateShader(shaderType);
        if (shaderId == 0) {
            throw new Exception("Error creating shader. Type: " + shaderType);
        }

        glShaderSource(shaderId, shaderCode);
        glCompileShader(shaderId);

        // Check for compilation errors
        int status = glGetShaderi(shaderId, GL_COMPILE_STATUS);
        if (status == 0) {
            String error = glGetShaderInfoLog(shaderId, 1024);
            System.err.println("Shader compilation error: " + error);
            System.err.println("Shader code:\n" + shaderCode);
            throw new Exception("Error compiling Shader code: " + error);
        }

        glAttachShader(programId, shaderId);
        return shaderId;
    }

    public void link() throws Exception {
        glLinkProgram(programId);
        
        // Check for linking errors
        int status = glGetProgrami(programId, GL_LINK_STATUS);
        if (status == 0) {
            String error = glGetProgramInfoLog(programId, 1024);
            System.err.println("Shader linking error: " + error);
            throw new Exception("Error linking Shader code: " + error);
        }

        if (vertexShaderId != 0) {
            glDetachShader(programId, vertexShaderId);
        }
        if (fragmentShaderId != 0) {
            glDetachShader(programId, fragmentShaderId);
        }
    }

    public void validate() {
        glValidateProgram(programId);
        int status = glGetProgrami(programId, GL_VALIDATE_STATUS);
        if (status == 0) {
            String error = glGetProgramInfoLog(programId, 1024);
            System.err.println("Shader validation error: " + error);
        }
    }

    public void bind() {
        glUseProgram(programId);
    }

    public void unbind() {
        glUseProgram(0);
    }

    public int getProgramId() {
        return programId;
    }

    public void cleanup() {
        unbind();
        if (programId != 0) {
            glDeleteProgram(programId);
        }
    }

    public void createUniform(String uniformName) throws Exception {
        int uniformLocation = glGetUniformLocation(programId, uniformName);
        if (uniformLocation < 0) {
            throw new Exception("Could not find uniform:" + uniformName);
        }
        uniforms.put(uniformName, uniformLocation);
    }

    public void setUniform(String uniformName, float value) {
        glUniform1f(uniforms.get(uniformName), value);
    }

    public void setUniform(String uniformName, int value) {
        glUniform1i(uniforms.get(uniformName), value);
    }

    public void setUniform(String uniformName, float[] value) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            FloatBuffer fb = stack.mallocFloat(value.length);
            fb.put(value).flip();
            glUniform1fv(uniforms.get(uniformName), fb);
        }
    }

    public void setUniform(String uniformName, Matrix4f value) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            FloatBuffer fb = stack.mallocFloat(16);
            value.store(fb);
            glUniformMatrix4fv(uniforms.get(uniformName), false, fb);
        }
    }
} 