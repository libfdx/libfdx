package io.github.libfdx.math;

/**
 * Represents a vector2.
 *
 * @author xpenatan
 */
public final class Vector2 {
    public static final Vector2 ZERO = new Vector2(0.0f, 0.0f);
    public static final Vector2 X = new Vector2(1.0f, 0.0f);
    public static final Vector2 Y = new Vector2(0.0f, 1.0f);

    private float x;
    private float y;

    /**
     * Creates a vector2.
     */
    public Vector2() {
    }

    /**
     * Creates a vector2.
     *
     * @param x the x coordinate
     * @param y the y coordinate
     */
    public Vector2(float x, float y) {
        set(x, y);
    }

    /**
     * Creates a vector2 from the supplied values.
     *
     * @param x the x coordinate
     * @param y the y coordinate
     * @return a new vector2
     */
    public static Vector2 of(float x, float y) {
        return new Vector2(x, y);
    }

    /**
     * Sets the set and returns this vector2.
     *
     * @param x the x coordinate
     * @param y the y coordinate
     * @return this vector2 for chaining
     */
    public Vector2 set(float x, float y) {
        this.x = x;
        this.y = y;
        return this;
    }

    /**
     * Sets the set and returns this vector2.
     *
     * @param other the other
     * @return this vector2 for chaining
     */
    public Vector2 set(Vector2 other) {
        return set(other.x, other.y);
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
     * Sets the add and returns this vector2.
     *
     * @param other the other
     * @return this vector2 for chaining
     */
    public Vector2 add(Vector2 other) {
        return new Vector2(x + other.x, y + other.y);
    }

    /**
     * Sets the subtract and returns this vector2.
     *
     * @param other the other
     * @return this vector2 for chaining
     */
    public Vector2 subtract(Vector2 other) {
        return new Vector2(x - other.x, y - other.y);
    }

    /**
     * Sets the scale and returns this vector2.
     *
     * @param scalar the scalar
     * @return this vector2 for chaining
     */
    public Vector2 scale(float scalar) {
        return new Vector2(x * scalar, y * scalar);
    }

    /**
     * Runs the dot step.
     *
     * @param other the other
     * @return the dot
     */
    public float dot(Vector2 other) {
        return x * other.x + y * other.y;
    }

    /**
     * Returns the length.
     *
     * @return the length
     */
    public float length() {
        return (float)Math.sqrt(dot(this));
    }

    /**
     * Returns the normalize.
     *
     * @return this vector2 for chaining
     */
    public Vector2 normalize() {
        float len = length();
        if (len == 0.0f) {
            return new Vector2();
        }
        return scale(1.0f / len);
    }
}
