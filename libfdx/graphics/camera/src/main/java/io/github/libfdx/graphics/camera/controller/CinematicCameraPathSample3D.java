package io.github.libfdx.graphics.camera.controller;

import io.github.libfdx.core.FdxException;

/**
 * Mutable no-allocation output value for 3D cinematic camera paths.
 *
 * @author xpenatan
 */
public final class CinematicCameraPathSample3D {
    private float cameraX;
    private float cameraY;
    private float cameraZ;
    private float lookAtX;
    private float lookAtY;
    private float lookAtZ;
    private float upX;
    private float upY = 1.0f;
    private float upZ;

    public CinematicCameraPathSample3D camera(float x, float y, float z) {
        validateFinite(x, "Camera path sample camera x");
        validateFinite(y, "Camera path sample camera y");
        validateFinite(z, "Camera path sample camera z");
        cameraX = x;
        cameraY = y;
        cameraZ = z;
        return this;
    }

    public CinematicCameraPathSample3D lookAt(float x, float y, float z) {
        validateFinite(x, "Camera path sample look-at x");
        validateFinite(y, "Camera path sample look-at y");
        validateFinite(z, "Camera path sample look-at z");
        lookAtX = x;
        lookAtY = y;
        lookAtZ = z;
        return this;
    }

    public CinematicCameraPathSample3D up(float x, float y, float z) {
        validateFinite(x, "Camera path sample up x");
        validateFinite(y, "Camera path sample up y");
        validateFinite(z, "Camera path sample up z");
        upX = x;
        upY = y;
        upZ = z;
        return this;
    }

    public float cameraX() {
        return cameraX;
    }

    public float cameraY() {
        return cameraY;
    }

    public float cameraZ() {
        return cameraZ;
    }

    public float lookAtX() {
        return lookAtX;
    }

    public float lookAtY() {
        return lookAtY;
    }

    public float lookAtZ() {
        return lookAtZ;
    }

    public float upX() {
        return upX;
    }

    public float upY() {
        return upY;
    }

    public float upZ() {
        return upZ;
    }

    private static void validateFinite(float value, String label) {
        if (!Float.isFinite(value)) {
            throw new FdxException(label + " must be finite");
        }
    }
}
