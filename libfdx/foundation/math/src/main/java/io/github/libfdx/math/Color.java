package io.github.libfdx.math;

import io.github.libfdx.core.FdxException;

public final class Color {
    public static final Color WHITE = new Color(1.0f, 1.0f, 1.0f, 1.0f);
    public static final Color BLACK = new Color(0.0f, 0.0f, 0.0f, 1.0f);
    public static final Color CLEAR = new Color(0.0f, 0.0f, 0.0f, 0.0f);

    private final float red;
    private final float green;
    private final float blue;
    private final float alpha;

    public Color(float red, float green, float blue, float alpha) {
        this.red = red;
        this.green = green;
        this.blue = blue;
        this.alpha = alpha;
    }

    public static Color rgba(float red, float green, float blue, float alpha) {
        return new Color(red, green, blue, alpha);
    }

    public static Color copyOf(Color color) {
        if (color == null) {
            throw new FdxException("Color cannot be null");
        }
        return new Color(color.red, color.green, color.blue, color.alpha);
    }

    public float red() {
        return red;
    }

    public float green() {
        return green;
    }

    public float blue() {
        return blue;
    }

    public float alpha() {
        return alpha;
    }
}
