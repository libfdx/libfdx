package io.github.libfdx.math;

import io.github.libfdx.core.FdxException;

public final class BoundingBox {
    private final Vector3 min;
    private final Vector3 max;

    public BoundingBox(Vector3 min, Vector3 max) {
        if (min == null || max == null) {
            throw new FdxException("BoundingBox min and max cannot be null");
        }
        this.min = min;
        this.max = max;
    }

    public static BoundingBox empty() {
        return new BoundingBox(Vector3.ZERO, Vector3.ZERO);
    }

    public static BoundingBox of(Vector3 min, Vector3 max) {
        return new BoundingBox(min, max);
    }

    public Vector3 min() {
        return min;
    }

    public Vector3 max() {
        return max;
    }
}
