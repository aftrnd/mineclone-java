package com.mineclone;

import com.mineclone.engine.IGameLogic;
import com.mineclone.engine.MouseInput;
import com.mineclone.engine.Window;

import static org.lwjgl.opengl.GL11.*;

public class DummyGame implements IGameLogic {
    private int direction = 1;
    private float color = 0.0f;

    public DummyGame() {
    }

    @Override
    public void init(Window window) throws Exception {
        // Nothing to be done here
    }

    @Override
    public void input(Window window, MouseInput mouseInput) {
        // Nothing to be done here
    }

    @Override
    public void update(float interval, MouseInput mouseInput) {
        color += direction * 0.01f;
        if (color > 1) {
            color = 1.0f;
            direction = -1;
        } else if (color < 0) {
            color = 0.0f;
            direction = 1;
        }
    }

    @Override
    public void render(Window window) {
        if (window.isResized()) {
            glViewport(0, 0, window.getWidth(), window.getHeight());
            window.setResized(false);
        }

        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
        glClearColor(color, color, color, 0.0f);
    }

    @Override
    public void cleanup() {
        // Nothing to be done here
    }
} 