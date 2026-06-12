package io.github.libfdx.ui;

/**
 * Represents an ui insets.
 *
 * @author xpenatan
 */
public final class UiInsets {
    public static final UiInsets ZERO = new UiInsets(0.0f, 0.0f, 0.0f, 0.0f);

    private final float left;
    private final float top;
    private final float right;
    private final float bottom;

    /**
     * Creates an UI insets.
     *
     * @param left the left
     * @param top the top
     * @param right the right
     * @param bottom the bottom
     */
    public UiInsets(float left, float top, float right, float bottom) {
        this.left = left;
        this.top = top;
        this.right = right;
        this.bottom = bottom;
    }

    /**
     * Creates an UI insets from the supplied values.
     *
     * @param all the all
     * @return a new UI insets
     */
    public static UiInsets of(float all) {
        return new UiInsets(all, all, all, all);
    }

    /**
     * Creates an UI insets from the supplied values.
     *
     * @param horizontal the horizontal
     * @param vertical the vertical
     * @return a new UI insets
     */
    public static UiInsets of(float horizontal, float vertical) {
        return new UiInsets(horizontal, vertical, horizontal, vertical);
    }

    /**
     * Creates an UI insets from the supplied values.
     *
     * @param left the left
     * @param top the top
     * @param right the right
     * @param bottom the bottom
     * @return a new UI insets
     */
    public static UiInsets of(float left, float top, float right, float bottom) {
        return new UiInsets(left, top, right, bottom);
    }

    /**
     * Returns the left.
     *
     * @return the left
     */
    public float left() {
        return left;
    }

    /**
     * Returns the top.
     *
     * @return the top
     */
    public float top() {
        return top;
    }

    /**
     * Returns the right.
     *
     * @return the right
     */
    public float right() {
        return right;
    }

    /**
     * Returns the bottom.
     *
     * @return the bottom
     */
    public float bottom() {
        return bottom;
    }

    /**
     * Returns the horizontal.
     *
     * @return the horizontal
     */
    public float horizontal() {
        return left + right;
    }

    /**
     * Returns the vertical.
     *
     * @return the vertical
     */
    public float vertical() {
        return top + bottom;
    }
}
