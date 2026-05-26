package io.github.libfdx.math;

public final class Quaternion {
    public static final Quaternion IDENTITY = new Quaternion(0.0f, 0.0f, 0.0f, 1.0f);

    private float x;
    private float y;
    private float z;
    private float w = 1.0f;

    public Quaternion() {
    }

    public Quaternion(float x, float y, float z, float w) {
        set(x, y, z, w);
    }

    public static Quaternion of(float x, float y, float z, float w) {
        return new Quaternion(x, y, z, w);
    }

    public Quaternion idt() {
        return set(0.0f, 0.0f, 0.0f, 1.0f);
    }

    public Quaternion set(float x, float y, float z, float w) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.w = w;
        return this;
    }

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
