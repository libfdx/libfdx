package io.github.libfdx.ui;

/**
 * Stores option values for an ui text area.
 *
 * @author xpenatan
 */
public final class UiTextAreaOptions {
    private static final UiTextAreaOptions DEFAULTS = new UiTextAreaOptions(false, 96.0f, Float.NaN);

    private final boolean autoGrow;
    private final float minHeight;
    private final float maxHeight;

    private UiTextAreaOptions(boolean autoGrow, float minHeight, float maxHeight) {
        this.autoGrow = autoGrow;
        this.minHeight = minHeight > 0.0f ? minHeight : 96.0f;
        this.maxHeight = maxHeight > 0.0f ? maxHeight : Float.NaN;
    }

    /**
     * Creates an UI text area options.
     *
     * @return a new UI text area options
     */
    public static UiTextAreaOptions defaults() {
        return DEFAULTS;
    }

    /**
     * Sets the auto grow and returns this UI text area options.
     *
     * @param autoGrow the auto grow
     * @return this UI text area options for chaining
     */
    public UiTextAreaOptions autoGrow(boolean autoGrow) {
        return new UiTextAreaOptions(autoGrow, minHeight, maxHeight);
    }

    /**
     * Sets the min height and returns this UI text area options.
     *
     * @param minHeight the min height
     * @return this UI text area options for chaining
     */
    public UiTextAreaOptions minHeight(float minHeight) {
        return new UiTextAreaOptions(autoGrow, minHeight, maxHeight);
    }

    /**
     * Sets the max height and returns this UI text area options.
     *
     * @param maxHeight the max height
     * @return this UI text area options for chaining
     */
    public UiTextAreaOptions maxHeight(float maxHeight) {
        return new UiTextAreaOptions(autoGrow, minHeight, maxHeight);
    }

    /**
     * Returns the auto grow.
     *
     * @return true if auto grow succeeds or is active; false otherwise
     */
    public boolean autoGrow() {
        return autoGrow;
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
     * Returns the max height.
     *
     * @return the max height
     */
    public float maxHeight() {
        return maxHeight;
    }
}
