package io.github.libfdx.math;

import io.github.libfdx.core.FdxException;

/**
 * Represents a bounding box.
 *
 * @author xpenatan
 */
public final class BoundingBox {
    private final Vector3 min;
    private final Vector3 max;

    /**
     * Creates a bounding box.
     *
     * @param min the min
     * @param max the max
     */
    public BoundingBox(Vector3 min, Vector3 max) {
        if (min == null || max == null) {
            throw new FdxException("BoundingBox min and max cannot be null");
        }
        this.min = min;
        this.max = max;
    }

    /**
     * Creates a bounding box.
     *
     * @return a new bounding box
     */
    public static BoundingBox empty() {
        return new BoundingBox(Vector3.ZERO, Vector3.ZERO);
    }

    /**
     * Creates a bounding box from the supplied values.
     *
     * @param min the min
     * @param max the max
     * @return a new bounding box
     */
    public static BoundingBox of(Vector3 min, Vector3 max) {
        return new BoundingBox(min, max);
    }

    /**
     * Returns the min.
     *
     * @return the min
     */
    public Vector3 min() {
        return min;
    }

    /**
     * Returns the max.
     *
     * @return the max
     */
    public Vector3 max() {
        return max;
    }
}
