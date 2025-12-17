package com.mineclone.core;

import org.lwjgl.glfw.*;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;

import static org.lwjgl.glfw.Callbacks.glfwFreeCallbacks;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.system.MemoryUtil.NULL;

public class Window {
    private final String title;
    private int width;
    private int height;
    private long windowHandle;
    private boolean resized;
    private boolean vSync;
    
    // Minecraft-style: Track key states for PRESS events only (not held)
    private final boolean[] keyPressed = new boolean[GLFW_KEY_LAST + 1];
    private final boolean[] keyJustPressed = new boolean[GLFW_KEY_LAST + 1];
    
    public Window(String title, int width, int height, boolean vSync) {
        this.title = title;
        this.width = width;
        this.height = height;
        this.vSync = vSync;
        this.resized = false;
    }
    
    public void init() {
        // Setup an error callback
        GLFWErrorCallback.createPrint(System.err).set();
        
        // Initialize GLFW
        if (!glfwInit()) {
            throw new IllegalStateException("Unable to initialize GLFW");
        }
        
        // Configure GLFW
        glfwDefaultWindowHints();
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
        glfwWindowHint(GLFW_RESIZABLE, GLFW_TRUE);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 2);
        glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
        glfwWindowHint(GLFW_OPENGL_FORWARD_COMPAT, GLFW_TRUE);
        
        // Create the window
        windowHandle = glfwCreateWindow(width, height, title, NULL, NULL);
        if (windowHandle == NULL) {
            throw new RuntimeException("Failed to create the GLFW window");
        }
        
        // Setup resize callback
        glfwSetFramebufferSizeCallback(windowHandle, (window, width, height) -> {
            this.width = width;
            this.height = height;
            this.resized = true;
        });
        
        // Setup key callback (Minecraft-style event-based input)
        glfwSetKeyCallback(windowHandle, (window, key, scancode, action, mods) -> {
            if (key == GLFW_KEY_ESCAPE && action == GLFW_RELEASE) {
                glfwSetWindowShouldClose(window, true);
            }
            
            // Track key press/release events (not repeat!)
            if (key >= 0 && key <= GLFW_KEY_LAST) {
                if (action == GLFW_PRESS) {
                    keyPressed[key] = true;
                    keyJustPressed[key] = true;  // Mark as "just pressed" for this frame
                } else if (action == GLFW_RELEASE) {
                    keyPressed[key] = false;
                }
            }
        });
        
        // Make the OpenGL context current
        glfwMakeContextCurrent(windowHandle);
        
        // Enable v-sync
        if (vSync) {
            glfwSwapInterval(1);
        } else {
            glfwSwapInterval(0);
        }
        
        // Make the window visible
        glfwShowWindow(windowHandle);
        
        // This line is critical for LWJGL's interoperation with GLFW's
        // OpenGL context, or any context that is managed externally.
        // LWJGL detects the context that is current in the current thread,
        // creates the GLCapabilities instance and makes the OpenGL
        // bindings available for use.
        GL.createCapabilities();
        
        // Set the clear color
        glClearColor(0.0f, 0.0f, 0.0f, 0.0f);
        
        // Enable depth testing
        glEnable(GL_DEPTH_TEST);
    }
    
    public void setClearColor(float r, float g, float b, float alpha) {
        glClearColor(r, g, b, alpha);
    }
    
    /**
     * Check if key is currently held down (for continuous actions like movement).
     * Minecraft-style: Use for WASD movement.
     */
    public boolean isKeyPressed(int keyCode) {
        if (keyCode < 0 || keyCode > GLFW_KEY_LAST) return false;
        return keyPressed[keyCode];
    }
    
    /**
     * Check if key was JUST pressed this frame (not held).
     * Minecraft-style: Use for toggle actions like F, jumping, etc.
     * This returns true ONCE per key press, then resets next frame.
     */
    public boolean isKeyJustPressed(int keyCode) {
        if (keyCode < 0 || keyCode > GLFW_KEY_LAST) return false;
        return keyJustPressed[keyCode];
    }
    
    /**
     * Clear "just pressed" flags at end of frame (called by update()).
     * Minecraft does this to ensure actions trigger ONCE per press.
     */
    private void clearJustPressed() {
        for (int i = 0; i <= GLFW_KEY_LAST; i++) {
            keyJustPressed[i] = false;
        }
    }
    
    public boolean windowShouldClose() {
        return glfwWindowShouldClose(windowHandle);
    }
    
    public String getTitle() {
        return title;
    }
    
    public int getWidth() {
        return width;
    }
    
    public int getHeight() {
        return height;
    }
    
    public long getWindowHandle() {
        return windowHandle;
    }
    
    public boolean isResized() {
        return resized;
    }
    
    public void setResized(boolean resized) {
        this.resized = resized;
    }
    
    public boolean isvSync() {
        return vSync;
    }
    
    public void setvSync(boolean vSync) {
        this.vSync = vSync;
    }
    
    public void update() {
        glfwSwapBuffers(windowHandle);
        glfwPollEvents();
        clearJustPressed();  // Reset "just pressed" flags for next frame
    }
    
    public void cleanup() {
        glfwFreeCallbacks(windowHandle);
        glfwDestroyWindow(windowHandle);
        glfwTerminate();
        GLFWErrorCallback callback = glfwSetErrorCallback(null);
        if (callback != null) {
            callback.free();
        }
    }
    
    public void setCursorMode(int mode) {
        glfwSetInputMode(windowHandle, GLFW_CURSOR, mode);
    }
} 