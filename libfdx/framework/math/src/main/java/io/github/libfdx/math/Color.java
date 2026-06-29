package io.github.libfdx.math;

import io.github.libfdx.core.FdxException;

/**
 * Represents a color.
 *
 * @author xpenatan
 */
public final class Color {
    public static final Color WHITE = new Color(1.0f, 1.0f, 1.0f, 1.0f);
    public static final Color BLACK = new Color(0.0f, 0.0f, 0.0f, 1.0f);
    public static final Color CLEAR = new Color(0.0f, 0.0f, 0.0f, 0.0f);

    private final float red;
    private final float green;
    private final float blue;
    private final float alpha;

    /**
     * Creates a color.
     *
     * @param red the red
     * @param green the green
     * @param blue the blue
     * @param alpha the alpha
     */
    public Color(float red, float green, float blue, float alpha) {
        this.red = red;
        this.green = green;
        this.blue = blue;
        this.alpha = alpha;
    }

    /**
     * Creates a color.
     *
     * @param red the red
     * @param green the green
     * @param blue the blue
     * @param alpha the alpha
     * @return a new color
     */
    public static Color rgba(float red, float green, float blue, float alpha) {
        return new Color(red, green, blue, alpha);
    }

    /**
     * Creates a color.
     *
     * @param color the color
     * @return a new color
     */
    public static Color copyOf(Color color) {
        if (color == null) {
            throw new FdxException("Color cannot be null");
        }
        return new Color(color.red, color.green, color.blue, color.alpha);
    }

    /**
     * Returns the red.
     *
     * @return the red
     */
    public float red() {
        return red;
    }

    /**
     * Returns the green.
     *
     * @return the green
     */
    public float green() {
        return green;
    }

    /**
     * Returns the blue.
     *
     * @return the blue
     */
    public float blue() {
        return blue;
    }

    /**
     * Returns the alpha.
     *
     * @return the alpha
     */
    public float alpha() {
        return alpha;
    }
}
