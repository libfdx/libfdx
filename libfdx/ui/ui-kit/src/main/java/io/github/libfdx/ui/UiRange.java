package io.github.libfdx.ui;

public final class UiRange {
    private final float minimum;
    private final float maximum;

    public UiRange(float minimum, float maximum) {
        this.minimum = minimum;
        this.maximum = Math.max(minimum, maximum);
    }

    public float minimum() {
        return minimum;
    }

    public float maximum() {
        return maximum;
    }

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
