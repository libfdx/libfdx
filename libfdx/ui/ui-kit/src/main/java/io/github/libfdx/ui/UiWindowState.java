package io.github.libfdx.ui;

public final class UiWindowState {
    private float x;
    private float y;
    private float width;
    private float height;
    private float minWidth = 160.0f;
    private float minHeight = 120.0f;
    private int zOrder;

    public UiWindowState(float x, float y, float width, float height) {
        this.x = x;
        this.y = y;
        this.width = Math.max(minWidth, width);
        this.height = Math.max(minHeight, height);
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

    public float minWidth() {
        return minWidth;
    }

    public float minHeight() {
        return minHeight;
    }

    public int zOrder() {
        return zOrder;
    }

    public UiWindowState position(float x, float y) {
        this.x = x;
        this.y = y;
        return this;
    }

    public UiWindowState size(float width, float height) {
        this.width = Math.max(minWidth, width);
        this.height = Math.max(minHeight, height);
        return this;
    }

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
