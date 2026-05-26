package io.github.libfdx.ui;

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

    public static UiTextAreaOptions defaults() {
        return DEFAULTS;
    }

    public UiTextAreaOptions autoGrow(boolean autoGrow) {
        return new UiTextAreaOptions(autoGrow, minHeight, maxHeight);
    }

    public UiTextAreaOptions minHeight(float minHeight) {
        return new UiTextAreaOptions(autoGrow, minHeight, maxHeight);
    }

    public UiTextAreaOptions maxHeight(float maxHeight) {
        return new UiTextAreaOptions(autoGrow, minHeight, maxHeight);
    }

    public boolean autoGrow() {
        return autoGrow;
    }

    public float minHeight() {
        return minHeight;
    }

    public float maxHeight() {
        return maxHeight;
    }
}
