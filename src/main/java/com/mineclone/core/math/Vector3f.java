package com.mineclone.core.math;

public class Vector3f {
    public float x;
    public float y;
    public float z;

    public Vector3f() {
        this.x = 0;
        this.y = 0;
        this.z = 0;
    }

    public Vector3f(float x, float y, float z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public Vector3f(Vector3f v) {
        this.x = v.x;
        this.y = v.y;
        this.z = v.z;
    }

    public void set(float x, float y, float z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public void set(Vector3f v) {
        this.x = v.x;
        this.y = v.y;
        this.z = v.z;
    }

    public void add(Vector3f v) {
        this.x += v.x;
        this.y += v.y;
        this.z += v.z;
    }

    public void sub(Vector3f v) {
        this.x -= v.x;
        this.y -= v.y;
        this.z -= v.z;
    }

    public void mul(float scalar) {
        this.x *= scalar;
        this.y *= scalar;
        this.z *= scalar;
    }

    public void div(float scalar) {
        this.x /= scalar;
        this.y /= scalar;
        this.z /= scalar;
    }

    public float length() {
        return (float) Math.sqrt(x * x + y * y + z * z);
    }

    public void normalize() {
        float length = length();
        if (length != 0) {
            div(length);
        }
    }

    public float dot(Vector3f v) {
        return x * v.x + y * v.y + z * v.z;
    }

    public Vector3f cross(Vector3f v) {
        return new Vector3f(
            y * v.z - z * v.y,
            z * v.x - x * v.z,
            x * v.y - y * v.x
        );
    }

    @Override
    public String toString() {
        return "Vector3f{" +
                "x=" + x +
                ", y=" + y +
                ", z=" + z +
                '}';
    }
} 