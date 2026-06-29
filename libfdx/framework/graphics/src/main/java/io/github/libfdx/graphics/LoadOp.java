package io.github.libfdx.graphics;

/**
 * Represents a load op.
 *
 * @author xpenatan
 */
public final class LoadOp {
    private static final LoadOp LOAD = new LoadOp(false, 0.0f, 0.0f, 0.0f, 0.0f);

    private final boolean clear;
    private final float red;
    private final float green;
    private final float blue;
    private final float alpha;

    private LoadOp(boolean clear, float red, float green, float blue, float alpha) {
        this.clear = clear;
        this.red = red;
        this.green = green;
        this.blue = blue;
        this.alpha = alpha;
    }

    /**
     * Creates a load op.
     *
     * @param red the red
     * @param green the green
     * @param blue the blue
     * @param alpha the alpha
     * @return a new load op
     */
    public static LoadOp clear(float red, float green, float blue, float alpha) {
        return new LoadOp(true, red, green, blue, alpha);
    }

    /**
     * Creates a load op.
     *
     * @return a new load op
     */
    public static LoadOp load() {
        return LOAD;
    }

    /**
     * Returns whether clear is enabled or true.
     *
     * @return true if clear is enabled or true; false otherwise
     */
    public boolean isClear() {
        return clear;
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
