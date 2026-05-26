package io.github.libfdx.ui;

public final class UiSize {
    private final float width;
    private final float height;

    public UiSize(float width, float height) {
        this.width = width;
        this.height = height;
    }

    public float width() {
        return width;
    }

    public float height() {
        return height;
    }
}
