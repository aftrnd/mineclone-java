package com.mineclone.core.utils;

public class Vector2f {
    public float x;
    public float y;

    public Vector2f() {
        this.x = 0;
        this.y = 0;
    }

    public Vector2f(float x, float y) {
        this.x = x;
        this.y = y;
    }

    public Vector2f(Vector2f v) {
        this.x = v.x;
        this.y = v.y;
    }

    public void set(float x, float y) {
        this.x = x;
        this.y = y;
    }

    public void set(Vector2f v) {
        this.x = v.x;
        this.y = v.y;
    }

    public void add(Vector2f v) {
        this.x += v.x;
        this.y += v.y;
    }

    public void sub(Vector2f v) {
        this.x -= v.x;
        this.y -= v.y;
    }

    public void mul(float scalar) {
        this.x *= scalar;
        this.y *= scalar;
    }

    public void div(float scalar) {
        this.x /= scalar;
        this.y /= scalar;
    }

    public float length() {
        return (float) Math.sqrt(x * x + y * y);
    }

    public void normalize() {
        float length = length();
        if (length != 0) {
            div(length);
        }
    }

    public float dot(Vector2f v) {
        return x * v.x + y * v.y;
    }

    @Override
    public String toString() {
        return "Vector2f{" +
                "x=" + x +
                ", y=" + y +
                '}';
    }
} 