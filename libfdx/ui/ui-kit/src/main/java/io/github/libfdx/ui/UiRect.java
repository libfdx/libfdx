package io.github.libfdx.ui;

public final class UiRect {
    public static final UiRect ZERO = new UiRect(0.0f, 0.0f, 0.0f, 0.0f);

    private final float x;
    private final float y;
    private final float width;
    private final float height;

    public UiRect(float x, float y, float width, float height) {
        this.x = x;
        this.y = y;
        this.width = Math.max(0.0f, width);
        this.height = Math.max(0.0f, height);
    }

    public float x() {
        return x;
    }

    public float y() {
        return y;
    }

    public float width() {
        return width;
    }

    public float height() {
        return height;
    }

    public float right() {
        return x + width;
    }

    public float bottom() {
        return y + height;
    }

    public boolean contains(float px, float py) {
        return px >= x && py >= y && px <= right() && py <= bottom();
    }

    public UiRect inset(UiInsets insets) {
        UiInsets value = insets != null ? insets : UiInsets.ZERO;
        return new UiRect(x + value.left(), y + value.top(), width - value.horizontal(), height - value.vertical());
    }
}
