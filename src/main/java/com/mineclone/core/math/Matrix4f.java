package com.mineclone.core.math;

import java.nio.FloatBuffer;

public class Matrix4f {
    private float[] matrix;

    public Matrix4f() {
        matrix = new float[16];
        setIdentity();
    }

    public void setIdentity() {
        matrix[0] = 1.0f;
        matrix[1] = 0.0f;
        matrix[2] = 0.0f;
        matrix[3] = 0.0f;
        matrix[4] = 0.0f;
        matrix[5] = 1.0f;
        matrix[6] = 0.0f;
        matrix[7] = 0.0f;
        matrix[8] = 0.0f;
        matrix[9] = 0.0f;
        matrix[10] = 1.0f;
        matrix[11] = 0.0f;
        matrix[12] = 0.0f;
        matrix[13] = 0.0f;
        matrix[14] = 0.0f;
        matrix[15] = 1.0f;
    }

    public void setOrthographic(float left, float right, float bottom, float top, float near, float far) {
        matrix[0] = 2.0f / (right - left);
        matrix[1] = 0.0f;
        matrix[2] = 0.0f;
        matrix[3] = 0.0f;
        matrix[4] = 0.0f;
        matrix[5] = 2.0f / (top - bottom);
        matrix[6] = 0.0f;
        matrix[7] = 0.0f;
        matrix[8] = 0.0f;
        matrix[9] = 0.0f;
        matrix[10] = -2.0f / (far - near);
        matrix[11] = 0.0f;
        matrix[12] = -(right + left) / (right - left);
        matrix[13] = -(top + bottom) / (top - bottom);
        matrix[14] = -(far + near) / (far - near);
        matrix[15] = 1.0f;
    }

    public void setPerspective(float fov, float aspectRatio, float near, float far) {
        float yScale = (float) (1.0f / Math.tan(Math.toRadians(fov / 2.0f)));
        float xScale = yScale / aspectRatio;
        float frustumLength = far - near;

        matrix[0] = xScale;
        matrix[1] = 0.0f;
        matrix[2] = 0.0f;
        matrix[3] = 0.0f;
        matrix[4] = 0.0f;
        matrix[5] = yScale;
        matrix[6] = 0.0f;
        matrix[7] = 0.0f;
        matrix[8] = 0.0f;
        matrix[9] = 0.0f;
        matrix[10] = (far + near) / frustumLength;
        matrix[11] = 1.0f;
        matrix[12] = 0.0f;
        matrix[13] = 0.0f;
        matrix[14] = -(2.0f * near * far) / frustumLength;
        matrix[15] = 0.0f;

        System.out.println("Perspective matrix set with fov=" + fov + ", aspect=" + aspectRatio + ", near=" + near + ", far=" + far);
    }

    public void setTranslation(Vector3f vec) {
        matrix[12] = vec.x;
        matrix[13] = vec.y;
        matrix[14] = vec.z;
        System.out.println("Translation matrix set to: " + vec);
    }

    public void setRotation(float angle, Vector3f axis) {
        float c = (float) Math.cos(Math.toRadians(angle));
        float s = (float) Math.sin(Math.toRadians(angle));
        float oneminusc = 1.0f - c;
        float xy = axis.x * axis.y;
        float yz = axis.y * axis.z;
        float xz = axis.x * axis.z;
        float xs = axis.x * s;
        float ys = axis.y * s;
        float zs = axis.z * s;

        float f00 = axis.x * axis.x * oneminusc + c;
        float f01 = xy * oneminusc + zs;
        float f02 = xz * oneminusc - ys;
        float f10 = xy * oneminusc - zs;
        float f11 = axis.y * axis.y * oneminusc + c;
        float f12 = yz * oneminusc + xs;
        float f20 = xz * oneminusc + ys;
        float f21 = yz * oneminusc - xs;
        float f22 = axis.z * axis.z * oneminusc + c;

        matrix[0] = f00;
        matrix[1] = f01;
        matrix[2] = f02;
        matrix[3] = 0.0f;
        matrix[4] = f10;
        matrix[5] = f11;
        matrix[6] = f12;
        matrix[7] = 0.0f;
        matrix[8] = f20;
        matrix[9] = f21;
        matrix[10] = f22;
        matrix[11] = 0.0f;
        matrix[12] = 0.0f;
        matrix[13] = 0.0f;
        matrix[14] = 0.0f;
        matrix[15] = 1.0f;

        System.out.println("Rotation matrix set with angle=" + angle + ", axis=" + axis);
    }

    public void setScale(Vector3f vec) {
        matrix[0] = vec.x;
        matrix[5] = vec.y;
        matrix[10] = vec.z;
        System.out.println("Scale matrix set to: " + vec);
    }

    public void store(FloatBuffer buffer) {
        buffer.put(matrix);
    }

    public Matrix4f mul(Matrix4f right) {
        Matrix4f result = new Matrix4f();
        for (int i = 0; i < 4; i++) {
            for (int j = 0; j < 4; j++) {
                result.matrix[i * 4 + j] = 
                    matrix[i * 4] * right.matrix[j * 4] +
                    matrix[i * 4 + 1] * right.matrix[j * 4 + 1] +
                    matrix[i * 4 + 2] * right.matrix[j * 4 + 2] +
                    matrix[i * 4 + 3] * right.matrix[j * 4 + 3];
            }
        }
        return result;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Matrix4f[\n");
        for (int i = 0; i < 4; i++) {
            sb.append("  ");
            for (int j = 0; j < 4; j++) {
                sb.append(String.format("%.2f", matrix[i * 4 + j])).append(" ");
            }
            sb.append("\n");
        }
        sb.append("]");
        return sb.toString();
    }
} 