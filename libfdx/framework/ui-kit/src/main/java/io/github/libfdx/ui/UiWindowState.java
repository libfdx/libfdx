package io.github.libfdx.ui;

/**
 * Represents an ui window state.
 *
 * @author xpenatan
 */
public final class UiWindowState {
    private float x;
    private float y;
    private float width;
    private float height;
    private float minWidth = 160.0f;
    private float minHeight = 120.0f;
    private int zOrder;

    /**
     * Creates an UI window state.
     *
     * @param x the x coordinate
     * @param y the y coordinate
     * @param width the width in pixels
     * @param height the height in pixels
     */
    public UiWindowState(float x, float y, float width, float height) {
        this.x = x;
        this.y = y;
        this.width = Math.max(minWidth, width);
        this.height = Math.max(minHeight, height);
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
     * Returns the min width.
     *
     * @return the min width
     */
    public float minWidth() {
        return minWidth;
    }

    /**
     * Returns the min height.
     *
     * @return the min height
     */
    public float minHeight() {
        return minHeight;
    }

    /**
     * Returns the z order.
     *
     * @return the z order
     */
    public int zOrder() {
        return zOrder;
    }

    /**
     * Sets the position and returns this UI window state.
     *
     * @param x the x coordinate
     * @param y the y coordinate
     * @return this UI window state for chaining
     */
    public UiWindowState position(float x, float y) {
        this.x = x;
        this.y = y;
        return this;
    }

    /**
     * Sets the size and returns this UI window state.
     *
     * @param width the width in pixels
     * @param height the height in pixels
     * @return this UI window state for chaining
     */
    public UiWindowState size(float width, float height) {
        this.width = Math.max(minWidth, width);
        this.height = Math.max(minHeight, height);
        return this;
    }

    /**
     * Sets the min size and returns this UI window state.
     *
     * @param minWidth the min width
     * @param minHeight the min height
     * @return this UI window state for chaining
     */
    public UiWindowState minSize(float minWidth, float minHeight) {
        this.minWidth = Math.max(1.0f, minWidth);
        this.minHeight = Math.max(1.0f, minHeight);
        size(width, height);
        return this;
    }

    UiWindowState zOrder(int zOrder) {
        this.zOrder = zOrder;
        return this;
    }

    void clamp(UiRect area) {
        if (area == null || area.width() <= 0.0f || area.height() <= 0.0f) {
            return;
        }
        width = Math.max(minWidth, Math.min(width, Math.max(minWidth, area.width())));
        height = Math.max(minHeight, Math.min(height, Math.max(minHeight, area.height())));
        x = Math.max(area.x(), Math.min(x, area.right() - width));
        y = Math.max(area.y(), Math.min(y, area.bottom() - height));
    }
}
