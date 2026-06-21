package io.github.libfdx.graphics.camera.controller;

/**
 * Samples a 3D cinematic camera path into a caller-owned output value.
 *
 * @author xpenatan
 */
public interface CinematicCameraPath3D {
    void sample(float timeSeconds, CinematicCameraPathSample3D out);
}
