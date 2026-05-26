package io.github.libfdx.math;

public final class Vector4 {
    public static final Vector4 ZERO = new Vector4(0.0f, 0.0f, 0.0f, 0.0f);

    private float x;
    private float y;
    private float z;
    private float w;

    public Vector4() {
    }

    public Vector4(float x, float y, float z, float w) {
        set(x, y, z, w);
    }

    public static Vector4 of(float x, float y, float z, float w) {
        return new Vector4(x, y, z, w);
    }

    public Vector4 set(float x, float y, float z, float w) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.w = w;
        return this;
    }

    public Vector4 set(Vector4 other) {
        return set(other.x, other.y, other.z, other.w);
    }

    public float x() {
        return x;
    }

    public float y() {
        return y;
    }

    public float z() {
        return z;
    }

    public float w() {
        return w;
    }
}
