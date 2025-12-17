package com.mineclone.core.engine;

import org.lwjgl.glfw.*;
import org.lwjgl.opengl.*;
import org.lwjgl.system.*;

import java.nio.*;

import static org.lwjgl.glfw.Callbacks.*;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.system.MemoryStack.*;
import static org.lwjgl.system.MemoryUtil.*;

public class Window {
    private long window;
    private int width;
    private int height;
    private String title;
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
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3);
        glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);

        // Create the window
        window = glfwCreateWindow(width, height, title, NULL, NULL);
        if (window == NULL) {
            throw new RuntimeException("Failed to create the GLFW window");
        }

        // Setup resize callback
        glfwSetFramebufferSizeCallback(window, (window, width, height) -> {
            this.width = width;
            this.height = height;
            this.resized = true;
        });

        // Setup key callback (Minecraft-style event-based input)
        glfwSetKeyCallback(window, (window, key, scancode, action, mods) -> {
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

        // Get the thread stack and push a new frame
        try (MemoryStack stack = stackPush()) {
            IntBuffer pWidth = stack.mallocInt(1);
            IntBuffer pHeight = stack.mallocInt(1);

            // Get the window size passed to glfwCreateWindow
            glfwGetWindowSize(window, pWidth, pHeight);

            // Get the resolution of the primary monitor
            GLFWVidMode vidmode = glfwGetVideoMode(glfwGetPrimaryMonitor());

            // Center the window
            glfwSetWindowPos(
                window,
                (vidmode.width() - pWidth.get(0)) / 2,
                (vidmode.height() - pHeight.get(0)) / 2
            );
        }

        // Make the OpenGL context current
        glfwMakeContextCurrent(window);

        // Enable v-sync
        if (vSync) {
            glfwSwapInterval(1);
        }

        // Make the window visible
        glfwShowWindow(window);

        // Create OpenGL capabilities
        GL.createCapabilities();
        System.out.println("OpenGL Version: " + glGetString(GL_VERSION));
        System.out.println("OpenGL Vendor: " + glGetString(GL_VENDOR));
        System.out.println("OpenGL Renderer: " + glGetString(GL_RENDERER));

        // Set the clear color
        glClearColor(0.0f, 0.0f, 0.0f, 0.0f);
        
        // Enable depth testing
        glEnable(GL_DEPTH_TEST);
        
        // Enable blending
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        
        // Print OpenGL state
        System.out.println("Depth test enabled: " + glIsEnabled(GL_DEPTH_TEST));
        System.out.println("Blending enabled: " + glIsEnabled(GL_BLEND));
    }

    public void update() {
        // Swap the buffers
        glfwSwapBuffers(window);
        
        // Poll for window events (this fires callbacks that set keyJustPressed)
        glfwPollEvents();
    }
    
    /**
     * Clear "just pressed" flags (Minecraft does this at START of input handling).
     * Call this BEFORE checking isKeyJustPressed() in game logic.
     */
    public void clearJustPressed() {
        for (int i = 0; i <= GLFW_KEY_LAST; i++) {
            keyJustPressed[i] = false;
        }
    }

    public boolean shouldClose() {
        return glfwWindowShouldClose(window);
    }

    public void cleanup() {
        glfwFreeCallbacks(window);
        glfwDestroyWindow(window);
        glfwTerminate();
        glfwSetErrorCallback(null).free();
    }

    public boolean isResized() {
        return resized;
    }

    public void setResized(boolean resized) {
        this.resized = resized;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public long getWindowHandle() {
        return window;
    }

    public boolean isvSync() {
        return vSync;
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
    
    public void setCursorMode(int mode) {
        glfwSetInputMode(window, GLFW_CURSOR, mode);
    }
} 