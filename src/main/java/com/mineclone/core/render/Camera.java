package com.mineclone.core.render;

import com.mineclone.core.math.Matrix4f;
import com.mineclone.core.math.Vector3f;

public class Camera {
    private Vector3f position;
    private Vector3f rotation;
    private Matrix4f viewMatrix;

    public Camera() {
        position = new Vector3f(0, 0, 0);
        rotation = new Vector3f(0, 0, 0);
        viewMatrix = new Matrix4f();
        updateViewMatrix();
    }

    public void setPosition(float x, float y, float z) {
        position.set(x, y, z);
        System.out.println("Camera position set to: " + position);
        updateViewMatrix();
    }

    public void setRotation(float x, float y, float z) {
        rotation.set(x, y, z);
        System.out.println("Camera rotation set to: " + rotation);
        updateViewMatrix();
    }

    public void move(float offsetX, float offsetY, float offsetZ) {
        if (offsetZ != 0) {
            position.x += (float) Math.sin(Math.toRadians(rotation.y)) * -1.0f * offsetZ;
            position.z += (float) Math.cos(Math.toRadians(rotation.y)) * offsetZ;
        }
        if (offsetX != 0) {
            position.x += (float) Math.sin(Math.toRadians(rotation.y - 90)) * -1.0f * offsetX;
            position.z += (float) Math.cos(Math.toRadians(rotation.y - 90)) * offsetX;
        }
        position.y += offsetY;
        System.out.println("Camera moved to: " + position);
        updateViewMatrix();
    }

    public void rotate(float offsetX, float offsetY, float offsetZ) {
        rotation.x += offsetX;
        rotation.y += offsetY;
        rotation.z += offsetZ;
        System.out.println("Camera rotated to: " + rotation);
        updateViewMatrix();
    }

    public Vector3f getPosition() {
        return position;
    }

    public Matrix4f getViewMatrix() {
        return viewMatrix;
    }

    private void updateViewMatrix() {
        // Create a new matrix for the view transformation
        Matrix4f view = new Matrix4f();
        view.setIdentity();

        // First rotate the view
        Matrix4f rotX = new Matrix4f();
        rotX.setRotation(rotation.x, new Vector3f(1, 0, 0));
        Matrix4f rotY = new Matrix4f();
        rotY.setRotation(rotation.y, new Vector3f(0, 1, 0));
        Matrix4f rotZ = new Matrix4f();
        rotZ.setRotation(rotation.z, new Vector3f(0, 0, 1));

        // Combine rotations in the correct order (yaw, pitch, roll)
        view = rotZ.mul(rotX.mul(rotY));

        // Then translate the view
        Matrix4f trans = new Matrix4f();
        trans.setTranslation(new Vector3f(-position.x, -position.y, -position.z));
        view = view.mul(trans);

        // Store the result
        viewMatrix = view;

        System.out.println("View matrix updated: " + viewMatrix);
    }
} 