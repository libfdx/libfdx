package io.github.libfdx.math;

/**
 * Represents a quaternion.
 *
 * @author xpenatan
 */
public final class Quaternion {
    public static final Quaternion IDENTITY = new Quaternion(0.0f, 0.0f, 0.0f, 1.0f);

    private float x;
    private float y;
    private float z;
    private float w = 1.0f;

    /**
     * Creates a quaternion.
     */
    public Quaternion() {
    }

    /**
     * Creates a quaternion.
     *
     * @param x the x coordinate
     * @param y the y coordinate
     * @param z the z coordinate
     * @param w the w
     */
    public Quaternion(float x, float y, float z, float w) {
        set(x, y, z, w);
    }

    /**
     * Creates a quaternion from the supplied values.
     *
     * @param x the x coordinate
     * @param y the y coordinate
     * @param z the z coordinate
     * @param w the w
     * @return a new quaternion
     */
    public static Quaternion of(float x, float y, float z, float w) {
        return new Quaternion(x, y, z, w);
    }

    /**
     * Returns the idt.
     *
     * @return this quaternion for chaining
     */
    public Quaternion idt() {
        return set(0.0f, 0.0f, 0.0f, 1.0f);
    }

    /**
     * Sets the set and returns this quaternion.
     *
     * @param x the x coordinate
     * @param y the y coordinate
     * @param z the z coordinate
     * @param w the w
     * @return this quaternion for chaining
     */
    public Quaternion set(float x, float y, float z, float w) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.w = w;
        return this;
    }

    /**
     * Returns the normalize.
     *
     * @return this quaternion for chaining
     */
    public Quaternion normalize() {
        float len = (float)Math.sqrt(x * x + y * y + z * z + w * w);
        if (len == 0.0f) {
            return idt();
        }
        float invLen = 1.0f / len;
        x *= invLen;
        y *= invLen;
        z *= invLen;
        w *= invLen;
        return this;
    }

    /**
     * Returns the x.
     *
     * @return the x
     */
    public float x() {
        return x;
    }

    /**
     * Returns the y.
     *
     * @return the y
     */
    public float y() {
        return y;
    }

    /**
     * Returns the z.
     *
     * @return the z
     */
    public float z() {
        return z;
    }

    /**
     * Returns the w.
     *
     * @return the w
     */
    public float w() {
        return w;
    }
}
