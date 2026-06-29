package io.github.libfdx.ui;

/**
 * Represents an ui color.
 *
 * @author xpenatan
 */
public final class UiColor {
    public static final UiColor TRANSPARENT = rgba(0.0f, 0.0f, 0.0f, 0.0f);
    public static final UiColor WHITE = rgb(1.0f, 1.0f, 1.0f);
    public static final UiColor BLACK = rgb(0.0f, 0.0f, 0.0f);

    private final float red;
    private final float green;
    private final float blue;
    private final float alpha;

    private UiColor(float red, float green, float blue, float alpha) {
        this.red = clamp(red);
        this.green = clamp(green);
        this.blue = clamp(blue);
        this.alpha = clamp(alpha);
    }

    /**
     * Creates an UI color.
     *
     * @param red the red
     * @param green the green
     * @param blue the blue
     * @return a new UI color
     */
    public static UiColor rgb(float red, float green, float blue) {
        return rgba(red, green, blue, 1.0f);
    }

    /**
     * Creates an UI color.
     *
     * @param red the red
     * @param green the green
     * @param blue the blue
     * @param alpha the alpha
     * @return a new UI color
     */
    public static UiColor rgba(float red, float green, float blue, float alpha) {
        return new UiColor(red, green, blue, alpha);
    }

    /**
     * Creates an UI color.
     *
     * @param rgba the RGBA
     * @return a new UI color
     */
    public static UiColor rgba8888(int rgba) {
        float red = ((rgba >>> 24) & 0xff) / 255.0f;
        float green = ((rgba >>> 16) & 0xff) / 255.0f;
        float blue = ((rgba >>> 8) & 0xff) / 255.0f;
        float alpha = (rgba & 0xff) / 255.0f;
        return rgba(red, green, blue, alpha);
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

    private static float clamp(float value) {
        if (value < 0.0f) {
            return 0.0f;
        }
        if (value > 1.0f) {
            return 1.0f;
        }
        return value;
    }
}
