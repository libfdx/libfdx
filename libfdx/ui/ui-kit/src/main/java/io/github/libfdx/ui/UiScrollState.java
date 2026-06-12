package io.github.libfdx.ui;

/**
 * Represents an ui scroll state.
 *
 * @author xpenatan
 */
public final class UiScrollState {
    private float x;
    private float y;
    private float viewportWidth;
    private float viewportHeight;
    private float contentWidth;
    private float contentHeight;
    private float maxX = Float.NaN;
    private float maxY = Float.NaN;

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
     * Returns the viewport width.
     *
     * @return the viewport width
     */
    public float viewportWidth() {
        return viewportWidth;
    }

    /**
     * Returns the viewport height.
     *
     * @return the viewport height
     */
    public float viewportHeight() {
        return viewportHeight;
    }

    /**
     * Returns the content width.
     *
     * @return the content width
     */
    public float contentWidth() {
        return contentWidth;
    }

    /**
     * Returns the content height.
     *
     * @return the content height
     */
    public float contentHeight() {
        return contentHeight;
    }

    /**
     * Returns the max x.
     *
     * @return the max x
     */
    public float maxX() {
        return Float.isNaN(maxX) ? 0.0f : maxX;
    }

    /**
     * Returns the max y.
     *
     * @return the max y
     */
    public float maxY() {
        return Float.isNaN(maxY) ? 0.0f : maxY;
    }

    /**
     * Returns whether this instance can scroll x.
     *
     * @return true if can scroll x succeeds or is active; false otherwise
     */
    public boolean canScrollX() {
        return maxX() > 0.0f;
    }

    /**
     * Returns whether this instance can scroll y.
     *
     * @return true if can scroll y succeeds or is active; false otherwise
     */
    public boolean canScrollY() {
        return maxY() > 0.0f;
    }

    /**
     * Runs the scroll to step.
     *
     * @param x the x coordinate
     * @param y the y coordinate
     */
    public void scrollTo(float x, float y) {
        this.x = clamp(x, maxX);
        this.y = clamp(y, maxY);
    }

    /**
     * Runs the scroll by step.
     *
     * @param deltaX the delta x
     * @param deltaY the delta y
     */
    public void scrollBy(float deltaX, float deltaY) {
        scrollTo(x + deltaX, y + deltaY);
    }

    boolean updateMetrics(float viewportWidth, float viewportHeight, float contentWidth, float contentHeight) {
        this.viewportWidth = Math.max(0.0f, viewportWidth);
        this.viewportHeight = Math.max(0.0f, viewportHeight);
        this.contentWidth = Math.max(0.0f, contentWidth);
        this.contentHeight = Math.max(0.0f, contentHeight);
        this.maxX = Math.max(0.0f, this.contentWidth - this.viewportWidth);
        this.maxY = Math.max(0.0f, this.contentHeight - this.viewportHeight);
        float previousX = x;
        float previousY = y;
        scrollTo(x, y);
        return previousX != x || previousY != y;
    }

    private float clamp(float value, float max) {
        float result = Math.max(0.0f, value);
        if (!Float.isNaN(max)) {
            result = Math.min(result, Math.max(0.0f, max));
        }
        return result;
    }
}
