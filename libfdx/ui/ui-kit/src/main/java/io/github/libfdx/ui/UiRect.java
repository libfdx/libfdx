package io.github.libfdx.ui;

/**
 * Represents an ui rect.
 *
 * @author xpenatan
 */
public final class UiRect {
    public static final UiRect ZERO = new UiRect(0.0f, 0.0f, 0.0f, 0.0f);

    private final float x;
    private final float y;
    private final float width;
    private final float height;

    /**
     * Creates an UI rect.
     *
     * @param x the x coordinate
     * @param y the y coordinate
     * @param width the width in pixels
     * @param height the height in pixels
     */
    public UiRect(float x, float y, float width, float height) {
        this.x = x;
        this.y = y;
        this.width = Math.max(0.0f, width);
        this.height = Math.max(0.0f, height);
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

    /**
     * Returns the right.
     *
     * @return the right
     */
    public float right() {
        return x + width;
    }

    /**
     * Returns the bottom.
     *
     * @return the bottom
     */
    public float bottom() {
        return y + height;
    }

    /**
     * Runs the contains step.
     *
     * @param px the px
     * @param py the py
     * @return true if contains succeeds or is active; false otherwise
     */
    public boolean contains(float px, float py) {
        return px >= x && py >= y && px <= right() && py <= bottom();
    }

    /**
     * Sets the inset and returns this UI rect.
     *
     * @param insets the insets
     * @return this UI rect for chaining
     */
    public UiRect inset(UiInsets insets) {
        UiInsets value = insets != null ? insets : UiInsets.ZERO;
        return new UiRect(x + value.left(), y + value.top(), width - value.horizontal(), height - value.vertical());
    }
}
