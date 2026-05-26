package io.github.libfdx.math;

public final class Vector2 {
    public static final Vector2 ZERO = new Vector2(0.0f, 0.0f);
    public static final Vector2 X = new Vector2(1.0f, 0.0f);
    public static final Vector2 Y = new Vector2(0.0f, 1.0f);

    private float x;
    private float y;

    public Vector2() {
    }

    public Vector2(float x, float y) {
        set(x, y);
    }

    public static Vector2 of(float x, float y) {
        return new Vector2(x, y);
    }

    public Vector2 set(float x, float y) {
        this.x = x;
        this.y = y;
        return this;
    }

    public Vector2 set(Vector2 other) {
        return set(other.x, other.y);
    }

    public float x() {
        return x;
    }

    public float y() {
        return y;
    }

    public Vector2 add(Vector2 other) {
        return new Vector2(x + other.x, y + other.y);
    }

    public Vector2 subtract(Vector2 other) {
        return new Vector2(x - other.x, y - other.y);
    }

    public Vector2 scale(float scalar) {
        return new Vector2(x * scalar, y * scalar);
    }

    public float dot(Vector2 other) {
        return x * other.x + y * other.y;
    }

    public float length() {
        return (float)Math.sqrt(dot(this));
    }

    public Vector2 normalize() {
        float len = length();
        if (len == 0.0f) {
            return new Vector2();
        }
        return scale(1.0f / len);
    }
}
