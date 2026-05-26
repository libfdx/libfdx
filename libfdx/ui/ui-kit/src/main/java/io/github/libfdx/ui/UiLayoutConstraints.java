package io.github.libfdx.ui;

public final class UiLayoutConstraints {
    private final float minWidth;
    private final float minHeight;
    private final float maxWidth;
    private final float maxHeight;

    public UiLayoutConstraints(float minWidth, float minHeight, float maxWidth, float maxHeight) {
        this.minWidth = Math.max(0.0f, minWidth);
        this.minHeight = Math.max(0.0f, minHeight);
        this.maxWidth = Math.max(this.minWidth, maxWidth);
        this.maxHeight = Math.max(this.minHeight, maxHeight);
    }

    public UiSize size(float width, float height) {
        return new UiSize(clamp(width, minWidth, maxWidth), clamp(height, minHeight, maxHeight));
    }

    public float minWidth() {
        return minWidth;
    }

    public float minHeight() {
        return minHeight;
    }

    public float maxWidth() {
        return maxWidth;
    }

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
