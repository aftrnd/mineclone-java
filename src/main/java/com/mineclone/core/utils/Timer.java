package com.mineclone.core.utils;

public class Timer {
    private double lastLoopTime;
    private float timeCount;
    private int ups;
    private int fps;
    private int fpsCount;
    private int upsCount;

    public void init() {
        lastLoopTime = getTime();
        timeCount = 0;
        ups = 0;
        fps = 0;
        fpsCount = 0;
        upsCount = 0;
    }

    public double getTime() {
        return System.nanoTime() / 1_000_000_000.0;
    }

    public float getElapsedTime() {
        double time = getTime();
        float elapsedTime = (float) (time - lastLoopTime);
        lastLoopTime = time;
        timeCount += elapsedTime;
        return elapsedTime;
    }

    public void updateUPS() {
        upsCount++;
    }

    public void updateFPS() {
        fpsCount++;
    }

    public void update() {
        if (timeCount > 1f) {
            ups = upsCount;
            fps = fpsCount;
            upsCount = 0;
            fpsCount = 0;
            timeCount -= 1f;
        }
    }

    public int getUPS() {
        return ups;
    }

    public int getFPS() {
        return fps;
    }

    public double getLastLoopTime() {
        return lastLoopTime;
    }
} 