package io.github.libfdx.ui;

public final class UiInsets {
    public static final UiInsets ZERO = new UiInsets(0.0f, 0.0f, 0.0f, 0.0f);

    private final float left;
    private final float top;
    private final float right;
    private final float bottom;

    public UiInsets(float left, float top, float right, float bottom) {
        this.left = left;
        this.top = top;
        this.right = right;
        this.bottom = bottom;
    }

    public static UiInsets of(float all) {
        return new UiInsets(all, all, all, all);
    }

    public static UiInsets of(float horizontal, float vertical) {
        return new UiInsets(horizontal, vertical, horizontal, vertical);
    }

    public static UiInsets of(float left, float top, float right, float bottom) {
        return new UiInsets(left, top, right, bottom);
    }

    public float left() {
        return left;
    }

    public float top() {
        return top;
    }

    public float right() {
        return right;
    }

    public float bottom() {
        return bottom;
    }

    public float horizontal() {
        return left + right;
    }

    public float vertical() {
        return top + bottom;
    }
}
