package io.github.libfdx.ui;

/**
 * Represents an ui size.
 *
 * @author xpenatan
 */
public final class UiSize {
    private final float width;
    private final float height;

    /**
     * Creates an UI size.
     *
     * @param width the width in pixels
     * @param height the height in pixels
     */
    public UiSize(float width, float height) {
        this.width = width;
        this.height = height;
    }

    /**
     * Returns the width.
     *
     * @return the width
     */
    public float width() {
        return width;
    }

    /**
     * Returns the height.
     *
     * @return the height
     */
    public float height() {
        return height;
    }
}
