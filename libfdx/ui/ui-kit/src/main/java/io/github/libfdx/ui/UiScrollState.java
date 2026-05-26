package io.github.libfdx.ui;

public final class UiScrollState {
    private float x;
    private float y;
    private float viewportWidth;
    private float viewportHeight;
    private float contentWidth;
    private float contentHeight;
    private float maxX = Float.NaN;
    private float maxY = Float.NaN;

    public float x() {
        return x;
    }

    public float y() {
        return y;
    }

    public float viewportWidth() {
        return viewportWidth;
    }

    public float viewportHeight() {
        return viewportHeight;
    }

    public float contentWidth() {
        return contentWidth;
    }

    public float contentHeight() {
        return contentHeight;
    }

    public float maxX() {
        return Float.isNaN(maxX) ? 0.0f : maxX;
    }

    public float maxY() {
        return Float.isNaN(maxY) ? 0.0f : maxY;
    }

    public boolean canScrollX() {
        return maxX() > 0.0f;
    }

    public boolean canScrollY() {
        return maxY() > 0.0f;
    }

    public void scrollTo(float x, float y) {
        this.x = clamp(x, maxX);
        this.y = clamp(y, maxY);
    }

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
