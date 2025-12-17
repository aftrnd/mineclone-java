package com.mineclone.engine;

import com.mineclone.core.engine.Window;
import com.mineclone.core.engine.IGameLogic;
import com.mineclone.core.input.MouseInput;
import com.mineclone.core.utils.Timer;

/**
 * Game Engine with Minecraft-standard 20 TPS tick rate.
 * 
 * Architecture:
 * - Fixed 20 TPS (ticks per second) for deterministic physics
 * - Variable FPS for smooth rendering
 * - Partial tick calculation for interpolation
 * 
 * Matches Minecraft's exact tick system.
 */
public class Engine {
    // Minecraft standard: 20 TPS = 50ms per tick
    private static final int TARGET_TPS = 20;
    private static final float TICK_INTERVAL = 1.0f / TARGET_TPS;  // 0.05 seconds
    private static final int TARGET_FPS = 60;

    private final Window window;
    private final IGameLogic gameLogic;
    private final Timer timer;
    private final MouseInput mouseInput;

    public Engine(String windowTitle, int width, int height, boolean vSync, IGameLogic gameLogic) {
        window = new Window(windowTitle, width, height, vSync);
        this.gameLogic = gameLogic;
        timer = new Timer();
        mouseInput = new MouseInput();
    }

    public void run() {
        try {
            init();
            gameLoop();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            cleanup();
        }
    }

    private void init() throws Exception {
        window.init();
        timer.init();
        mouseInput.init(window);
        gameLogic.init(window);
    }

    /**
     * Main game loop with fixed 20 TPS tick rate.
     * 
     * Uses accumulator pattern to ensure consistent physics regardless of frame rate.
     * Calculates partial tick for smooth rendering interpolation.
     */
    private void gameLoop() {
        float accumulator = 0f;

        while (!window.shouldClose()) {
            float elapsedTime = timer.getElapsedTime();
            accumulator += elapsedTime;

            // Process input every frame
            input();

            // Fixed timestep updates (20 TPS)
            while (accumulator >= TICK_INTERVAL) {
                update(TICK_INTERVAL);
                accumulator -= TICK_INTERVAL;
            }

            // Calculate partial tick for interpolation (0.0 to 1.0)
            float partialTick = accumulator / TICK_INTERVAL;
            
            // Render with interpolation
            render(partialTick);

            // Frame rate limiting
            if (!window.isvSync()) {
                sync();
            }
        }
    }

    private void sync() {
        float loopSlot = 1f / TARGET_FPS;
        double endTime = timer.getLastLoopTime() + loopSlot;
        while (timer.getTime() < endTime) {
            try {
                Thread.sleep(1);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    private void input() {
        mouseInput.input(window);
        gameLogic.input(window, mouseInput);
    }

    private void update(float interval) {
        gameLogic.update(interval, mouseInput);
    }

    private void render(float partialTick) {
        gameLogic.render(window, partialTick);
        window.update();
    }

    private void cleanup() {
        gameLogic.cleanup();
        window.cleanup();
    }
}
