package io.github.libfdx.ui;

/**
 * Represents an ui range.
 *
 * @author xpenatan
 */
public final class UiRange {
    private final float minimum;
    private final float maximum;

    /**
     * Creates an UI range.
     *
     * @param minimum the minimum
     * @param maximum the maximum
     */
    public UiRange(float minimum, float maximum) {
        this.minimum = minimum;
        this.maximum = Math.max(minimum, maximum);
    }

    /**
     * Returns the minimum.
     *
     * @return the minimum
     */
    public float minimum() {
        return minimum;
    }

    /**
     * Returns the maximum.
     *
     * @return the maximum
     */
    public float maximum() {
        return maximum;
    }

    /**
     * Runs the clamp step.
     *
     * @param value the value
     * @return the clamp
     */
    public float clamp(float value) {
        if (value < minimum) {
            return minimum;
        }
        if (value > maximum) {
            return maximum;
        }
        return value;
    }
}
