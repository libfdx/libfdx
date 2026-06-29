package io.github.libfdx.ui;

/**
 * Represents an ui layout constraints.
 *
 * @author xpenatan
 */
public final class UiLayoutConstraints {
    private final float minWidth;
    private final float minHeight;
    private final float maxWidth;
    private final float maxHeight;

    /**
     * Creates an UI layout constraints.
     *
     * @param minWidth the min width
     * @param minHeight the min height
     * @param maxWidth the max width
     * @param maxHeight the max height
     */
    public UiLayoutConstraints(float minWidth, float minHeight, float maxWidth, float maxHeight) {
        this.minWidth = Math.max(0.0f, minWidth);
        this.minHeight = Math.max(0.0f, minHeight);
        this.maxWidth = Math.max(this.minWidth, maxWidth);
        this.maxHeight = Math.max(this.minHeight, maxHeight);
    }

    /**
     * Runs the size step.
     *
     * @param width the width in pixels
     * @param height the height in pixels
     * @return the size
     */
    public UiSize size(float width, float height) {
        return new UiSize(clamp(width, minWidth, maxWidth), clamp(height, minHeight, maxHeight));
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
     * Returns the max width.
     *
     * @return the max width
     */
    public float maxWidth() {
        return maxWidth;
    }

    /**
     * Returns the max height.
     *
     * @return the max height
     */
    public float maxHeight() {
        return maxHeight;
    }

    private static float clamp(float value, float minimum, float maximum) {
        if (value < minimum) {
            return minimum;
        }
        if (value > maximum) {
            return maximum;
        }
        return value;
    }
}
