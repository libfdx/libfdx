package io.github.libfdx.graphics.camera.controller;

import io.github.libfdx.math.Vector2;

/**
 * Supplies a 2D camera anchor position.
 *
 * @author xpenatan
 */
public interface CameraAnchor2D {
    /**
     * Copies the current anchor position into the supplied vector.
     *
     * @param out the output vector
     */
    void position(Vector2 out);
}
