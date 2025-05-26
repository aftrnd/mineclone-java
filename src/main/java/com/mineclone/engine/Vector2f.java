package com.mineclone.engine;

public class Vector2f {
    public float x;
    public float y;

    public Vector2f() {
        this(0, 0);
    }

    public Vector2f(float x, float y) {
        this.x = x;
        this.y = y;
    }

    public Vector2f(Vector2f vec) {
        this.x = vec.x;
        this.y = vec.y;
    }

    public Vector2f add(Vector2f vec) {
        return new Vector2f(x + vec.x, y + vec.y);
    }

    public Vector2f sub(Vector2f vec) {
        return new Vector2f(x - vec.x, y - vec.y);
    }

    public Vector2f mul(float scalar) {
        return new Vector2f(x * scalar, y * scalar);
    }

    public float length() {
        return (float) Math.sqrt(x * x + y * y);
    }

    public Vector2f normalize() {
        float len = length();
        return new Vector2f(x / len, y / len);
    }

    public float dot(Vector2f vec) {
        return x * vec.x + y * vec.y;
    }
} 