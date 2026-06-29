package io.github.libfdx.graphics.camera.controller;

import io.github.libfdx.math.Vector3;

/**
 * Supplies a 3D camera anchor position and local up direction.
 *
 * @author xpenatan
 */
public interface CameraAnchor3D {
    /**
     * Copies the current anchor position into the supplied vector.
     *
     * @param out the output vector
     */
    void position(Vector3 out);

    /**
     * Copies the current local up direction into the supplied vector.
     *
     * @param out the output vector
     */
    void up(Vector3 out);
}
