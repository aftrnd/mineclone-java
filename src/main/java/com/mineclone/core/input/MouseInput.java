package com.mineclone.core.input;

import com.mineclone.core.engine.Window;
import com.mineclone.core.utils.Vector2f;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWCursorPosCallback;
import org.lwjgl.glfw.GLFWMouseButtonCallback;
import org.lwjgl.glfw.GLFWScrollCallback;

import static org.lwjgl.glfw.GLFW.*;

public class MouseInput {
    private final Vector2f currentPos;
    private final Vector2f previousPos;
    private final Vector2f displVec;
    private final Vector2f scrollVec;
    private boolean inWindow;
    private boolean leftButtonPressed;
    private boolean rightButtonPressed;
    private boolean leftButtonClicked;   // Click event (consumed after read)
    private boolean rightButtonClicked;  // Click event (consumed after read)
    private GLFWCursorPosCallback cursorPosCallback;
    private GLFWMouseButtonCallback mouseButtonCallback;
    private GLFWScrollCallback scrollCallback;

    public MouseInput() {
        currentPos = new Vector2f();
        previousPos = new Vector2f();
        displVec = new Vector2f();
        scrollVec = new Vector2f();
        inWindow = false;
        leftButtonPressed = false;
        rightButtonPressed = false;
    }

    public void init(Window window) {
        // Set initial cursor position to center (1280x720)
        GLFW.glfwSetCursorPos(window.getWindowHandle(), 640, 360);
        currentPos.x = 640;
        currentPos.y = 360;
        previousPos.x = 640;
        previousPos.y = 360;

        GLFW.glfwSetCursorPosCallback(window.getWindowHandle(), cursorPosCallback = new GLFWCursorPosCallback() {
            @Override
            public void invoke(long window, double xpos, double ypos) {
                currentPos.x = (float) xpos;
                currentPos.y = (float) ypos;
            }
        });

        GLFW.glfwSetMouseButtonCallback(window.getWindowHandle(), mouseButtonCallback = new GLFWMouseButtonCallback() {
            @Override
            public void invoke(long window, int button, int action, int mods) {
                if (button == GLFW_MOUSE_BUTTON_LEFT) {
                    if (action == GLFW_PRESS) {
                        leftButtonPressed = true;
                        leftButtonClicked = true;  // Set click event
                    } else if (action == GLFW_RELEASE) {
                        leftButtonPressed = false;
                    }
                }
                if (button == GLFW_MOUSE_BUTTON_RIGHT) {
                    if (action == GLFW_PRESS) {
                        rightButtonPressed = true;
                        rightButtonClicked = true;  // Set click event
                    } else if (action == GLFW_RELEASE) {
                        rightButtonPressed = false;
                    }
                }
            }
        });

        GLFW.glfwSetScrollCallback(window.getWindowHandle(), scrollCallback = new GLFWScrollCallback() {
            @Override
            public void invoke(long window, double xoffset, double yoffset) {
                scrollVec.x = (float) xoffset;
                scrollVec.y = (float) yoffset;
            }
        });

        GLFW.glfwSetCursorEnterCallback(window.getWindowHandle(), (windowHandle, entered) -> {
            inWindow = entered;
        });
    }

    public Vector2f getCurrentPos() {
        return currentPos;
    }

    public Vector2f getDisplVec() {
        return displVec;
    }

    public Vector2f getScrollVec() {
        return scrollVec;
    }

    public boolean isLeftButtonPressed() {
        return leftButtonPressed;
    }

    public boolean isRightButtonPressed() {
        return rightButtonPressed;
    }
    
    /**
     * Check if left button was clicked (consumes the click event).
     */
    public boolean wasLeftButtonClicked() {
        boolean clicked = leftButtonClicked;
        leftButtonClicked = false;  // Consume the event
        return clicked;
    }
    
    /**
     * Check if right button was clicked (consumes the click event).
     */
    public boolean wasRightButtonClicked() {
        boolean clicked = rightButtonClicked;
        rightButtonClicked = false;  // Consume the event
        return clicked;
    }

    public boolean isInWindow() {
        return inWindow;
    }

    public void input(Window window) {
        // Calculate mouse displacement since last frame
        displVec.x = currentPos.x - previousPos.x;
        displVec.y = currentPos.y - previousPos.y;
        
        // Update previous position for next frame
        previousPos.x = currentPos.x;
        previousPos.y = currentPos.y;
        
        // Scroll resets each frame
        scrollVec.x = 0;
        scrollVec.y = 0;
    }

    public void resetToCenter(Window window) {
        double centerX = 640; // 1280 / 2
        double centerY = 360; // 720 / 2
        GLFW.glfwSetCursorPos(window.getWindowHandle(), centerX, centerY);
        currentPos.x = (float) centerX;
        currentPos.y = (float) centerY;
    }

    public void cleanup() {
        if (cursorPosCallback != null) cursorPosCallback.free();
        if (mouseButtonCallback != null) mouseButtonCallback.free();
        if (scrollCallback != null) scrollCallback.free();
    }
} 