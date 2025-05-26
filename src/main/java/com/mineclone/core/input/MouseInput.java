package com.mineclone.core.input;

import com.mineclone.core.engine.Window;
import com.mineclone.core.utils.Vector2f;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWCursorPosCallback;
import org.lwjgl.glfw.GLFWMouseButtonCallback;
import org.lwjgl.glfw.GLFWScrollCallback;

public class MouseInput {
    private final Vector2f currentPos;
    private final Vector2f displVec;
    private final Vector2f scrollVec;
    private boolean inWindow;
    private boolean leftButtonPressed;
    private boolean rightButtonPressed;
    private GLFWCursorPosCallback cursorPosCallback;
    private GLFWMouseButtonCallback mouseButtonCallback;
    private GLFWScrollCallback scrollCallback;

    public MouseInput() {
        currentPos = new Vector2f();
        displVec = new Vector2f();
        scrollVec = new Vector2f();
        inWindow = false;
        leftButtonPressed = false;
        rightButtonPressed = false;
    }

    public void init(Window window) {
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
                leftButtonPressed = button == GLFW.GLFW_MOUSE_BUTTON_1 && action == GLFW.GLFW_PRESS;
                rightButtonPressed = button == GLFW.GLFW_MOUSE_BUTTON_2 && action == GLFW.GLFW_PRESS;
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

    public boolean isInWindow() {
        return inWindow;
    }

    public void input(Window window) {
        displVec.x = 0;
        displVec.y = 0;
        scrollVec.x = 0;
        scrollVec.y = 0;
    }

    public void cleanup() {
        cursorPosCallback.free();
        mouseButtonCallback.free();
        scrollCallback.free();
    }
} 