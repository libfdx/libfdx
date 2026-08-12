package io.github.libfdx.math;

import io.github.libfdx.core.FdxException;

/**
 * Represents a 3D ray with an origin and a normalized direction.
 *
 * @author xpenatan
 */
public final class Ray {
    private final Vector3 origin = new Vector3();
    private final Vector3 direction = new Vector3(0.0f, 0.0f, -1.0f);

    /**
     * Creates a ray at the origin pointing along negative Z.
     */
    public Ray() {
    }

    /**
     * Creates a ray from the supplied origin and direction.
     *
     * @param origin the origin
     * @param direction the direction
     */
    public Ray(Vector3 origin, Vector3 direction) {
        set(origin, direction);
    }

    /**
     * Sets this ray from another ray.
     *
     * @param ray the source ray
     * @return this ray for chaining
     */
    public Ray set(Ray ray) {
        if (ray == null) {
            throw new FdxException("Ray cannot be null");
        }
        return set(ray.origin, ray.direction);
    }

    /**
     * Sets this ray from the supplied origin and direction.
     *
     * @param origin the origin
     * @param direction the direction
     * @return this ray for chaining
     */
    public Ray set(Vector3 origin, Vector3 direction) {
        if (origin == null || direction == null) {
            throw new FdxException("Ray origin and direction cannot be null");
        }
        return set(origin.x(), origin.y(), origin.z(), direction.x(), direction.y(), direction.z());
    }

    /**
     * Sets this ray from the supplied components.
     *
     * @param originX the origin x coordinate
     * @param originY the origin y coordinate
     * @param originZ the origin z coordinate
     * @param directionX the direction x coordinate
     * @param directionY the direction y coordinate
     * @param directionZ the direction z coordinate
     * @return this ray for chaining
     */
    public Ray set(float originX, float originY, float originZ,
            float directionX, float directionY, float directionZ) {
        float directionLength = (float)Math.sqrt(directionX * directionX
                + directionY * directionY + directionZ * directionZ);
        if (directionLength == 0.0f) {
            throw new FdxException("Ray direction cannot be zero length");
        }
        float inverseDirectionLength = 1.0f / directionLength;
        origin.set(originX, originY, originZ);
        direction.set(directionX * inverseDirectionLength,
                directionY * inverseDirectionLength,
                directionZ * inverseDirectionLength);
        return this;
    }

    /**
     * Returns the origin.
     *
     * @return the origin
     */
    public Vector3 origin() {
        return origin;
    }

    /**
     * Returns the normalized direction.
     *
     * @return the direction
     */
    public Vector3 direction() {
        return direction;
    }

    /**
     * Writes the point at the supplied distance to the output vector.
     *
     * @param distance the distance along this ray
     * @param out the output vector
     * @return the output vector
     */
    public Vector3 pointAt(float distance, Vector3 out) {
        if (out == null) {
            throw new FdxException("Ray point output cannot be null");
        }
        return out.set(
                origin.x() + direction.x() * distance,
                origin.y() + direction.y() * distance,
                origin.z() + direction.z() * distance);
    }
}
