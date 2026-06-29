package io.github.libfdx.graphics.camera.controller;

/**
 * Filters pointer input by viewport or screen region.
 *
 * @author xpenatan
 */
public interface CameraPointerRegion {
    /**
     * Returns whether the supplied pointer coordinate is accepted.
     *
     * @param x the x coordinate
     * @param y the y coordinate
     * @return true if the coordinate belongs to this region
     */
    boolean contains(int x, int y);
}
