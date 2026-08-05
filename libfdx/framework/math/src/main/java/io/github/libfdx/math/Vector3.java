package io.github.libfdx.math;

/**
 * Represents a vector3.
 *
 * @author xpenatan
 */
public final class Vector3 {
    public static final Vector3 ZERO = new Vector3(0.0f, 0.0f, 0.0f);
    public static final Vector3 X = new Vector3(1.0f, 0.0f, 0.0f);
    public static final Vector3 Y = new Vector3(0.0f, 1.0f, 0.0f);
    public static final Vector3 Z = new Vector3(0.0f, 0.0f, 1.0f);

    private float x;
    private float y;
    private float z;

    /**
     * Creates a vector3.
     */
    public Vector3() {
    }

    /**
     * Creates a vector3.
     *
     * @param x the x coordinate
     * @param y the y coordinate
     * @param z the z coordinate
     */
    public Vector3(float x, float y, float z) {
        set(x, y, z);
    }

    /**
     * Creates a vector3 from the supplied values.
     *
     * @param x the x coordinate
     * @param y the y coordinate
     * @param z the z coordinate
     * @return a new vector3
     */
    public static Vector3 of(float x, float y, float z) {
        return new Vector3(x, y, z);
    }

    /**
     * Sets the set and returns this vector3.
     *
     * @param x the x coordinate
     * @param y the y coordinate
     * @param z the z coordinate
     * @return this vector3 for chaining
     */
    public Vector3 set(float x, float y, float z) {
        this.x = x;
        this.y = y;
        this.z = z;
        return this;
    }

    /**
     * Sets the set and returns this vector3.
     *
     * @param other the other
     * @return this vector3 for chaining
     */
    public Vector3 set(Vector3 other) {
        return set(other.x, other.y, other.z);
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
     * Sets the add and returns this vector3.
     *
     * @param other the other
     * @return this vector3 for chaining
     */
    public Vector3 add(Vector3 other) {
        return new Vector3(x + other.x, y + other.y, z + other.z);
    }

    /**
     * Sets the subtract and returns this vector3.
     *
     * @param other the other
     * @return this vector3 for chaining
     */
    public Vector3 subtract(Vector3 other) {
        return new Vector3(x - other.x, y - other.y, z - other.z);
    }

    /**
     * Sets the scale and returns this vector3.
     *
     * @param scalar the scalar
     * @return this vector3 for chaining
     */
    public Vector3 scale(float scalar) {
        return new Vector3(x * scalar, y * scalar, z * scalar);
    }

    /**
     * Runs the dot step.
     *
     * @param other the other
     * @return the dot
     */
    public float dot(Vector3 other) {
        return x * other.x + y * other.y + z * other.z;
    }

    /**
     * Sets the cross and returns this vector3.
     *
     * @param other the other
     * @return this vector3 for chaining
     */
    public Vector3 cross(Vector3 other) {
        return new Vector3(
                y * other.z - z * other.y,
                z * other.x - x * other.z,
                x * other.y - y * other.x);
    }

    /**
     * Rotates this vector around the supplied axis using the right-hand rule.
     *
     * @param axis the axis to rotate around
     * @param radians the rotation angle in radians
     * @return this vector for chaining
     */
    public Vector3 rotate(Vector3 axis, float radians) {
        float axisX = axis.x;
        float axisY = axis.y;
        float axisZ = axis.z;
        float axisLengthSquared = axisX * axisX + axisY * axisY + axisZ * axisZ;
        if (axisLengthSquared == 0.0f || radians == 0.0f) {
            return this;
        }

        float inverseAxisLength = 1.0f / (float)Math.sqrt(axisLengthSquared);
        axisX *= inverseAxisLength;
        axisY *= inverseAxisLength;
        axisZ *= inverseAxisLength;

        float vectorX = x;
        float vectorY = y;
        float vectorZ = z;
        float cos = (float)Math.cos(radians);
        float sin = (float)Math.sin(radians);
        float oneMinusCos = 1.0f - cos;
        float axisDotVector = axisX * vectorX + axisY * vectorY + axisZ * vectorZ;

        return set(
                vectorX * cos + (axisY * vectorZ - axisZ * vectorY) * sin
                        + axisX * axisDotVector * oneMinusCos,
                vectorY * cos + (axisZ * vectorX - axisX * vectorZ) * sin
                        + axisY * axisDotVector * oneMinusCos,
                vectorZ * cos + (axisX * vectorY - axisY * vectorX) * sin
                        + axisZ * axisDotVector * oneMinusCos);
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
     * @return this vector3 for chaining
     */
    public Vector3 normalize() {
        float len = length();
        if (len == 0.0f) {
            return new Vector3();
        }
        return scale(1.0f / len);
    }
}
