package io.github.libfdx.math;

/**
 * Represents a vector4.
 *
 * @author xpenatan
 */
public final class Vector4 {
    public static final Vector4 ZERO = new Vector4(0.0f, 0.0f, 0.0f, 0.0f);

    private float x;
    private float y;
    private float z;
    private float w;

    /**
     * Creates a vector4.
     */
    public Vector4() {
    }

    /**
     * Creates a vector4.
     *
     * @param x the x coordinate
     * @param y the y coordinate
     * @param z the z coordinate
     * @param w the w
     */
    public Vector4(float x, float y, float z, float w) {
        set(x, y, z, w);
    }

    /**
     * Creates a vector4 from the supplied values.
     *
     * @param x the x coordinate
     * @param y the y coordinate
     * @param z the z coordinate
     * @param w the w
     * @return a new vector4
     */
    public static Vector4 of(float x, float y, float z, float w) {
        return new Vector4(x, y, z, w);
    }

    /**
     * Sets the set and returns this vector4.
     *
     * @param x the x coordinate
     * @param y the y coordinate
     * @param z the z coordinate
     * @param w the w
     * @return this vector4 for chaining
     */
    public Vector4 set(float x, float y, float z, float w) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.w = w;
        return this;
    }

    /**
     * Sets the set and returns this vector4.
     *
     * @param other the other
     * @return this vector4 for chaining
     */
    public Vector4 set(Vector4 other) {
        return set(other.x, other.y, other.z, other.w);
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
