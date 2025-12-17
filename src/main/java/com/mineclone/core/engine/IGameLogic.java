package com.mineclone.core.engine;

import com.mineclone.core.input.MouseInput;

/**
 * Game logic interface for the engine.
 * 
 * The render method receives partialTick for smooth interpolation between physics ticks.
 */
public interface IGameLogic {
    void init(Window window) throws Exception;
    void input(Window window, MouseInput mouseInput);
    void update(float interval, MouseInput mouseInput);
    void render(Window window, float partialTick);
    void cleanup();
} 