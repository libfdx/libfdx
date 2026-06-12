package io.github.libfdx.input;

/**
 * Represents a pointer event.
 *
 * @author xpenatan
 */
public final class PointerEvent extends InputEvent {
    private final int pointerId;
    private final PointerType type;
    private final MouseButton button;
    private final int x;
    private final int y;
    private final float scrollX;
    private final float scrollY;

    /**
     * Creates a pointer event.
     *
     * @param timeNanos the time nanos
     * @param pointerId the pointer ID
     * @param type the expected Java type
     * @param button the button
     * @param x the x coordinate
     * @param y the y coordinate
     * @param scrollX the scroll x
     * @param scrollY the scroll y
     */
    public PointerEvent(long timeNanos, int pointerId, PointerType type, MouseButton button, int x, int y,
            float scrollX, float scrollY) {
        super(timeNanos);
        this.pointerId = pointerId;
        this.type = type != null ? type : PointerType.MOUSE;
        this.button = button != null ? button : MouseButton.UNKNOWN;
        this.x = x;
        this.y = y;
        this.scrollX = scrollX;
        this.scrollY = scrollY;
    }

    /**
     * Creates a pointer event.
     *
     * @param timeNanos the time nanos
     * @param x the x coordinate
     * @param y the y coordinate
     * @return a new pointer event
     */
    public static PointerEvent pointer(long timeNanos, int x, int y) {
        return new PointerEvent(timeNanos, 0, PointerType.MOUSE, MouseButton.UNKNOWN, x, y, 0.0f, 0.0f);
    }

    /**
     * Creates a pointer event.
     *
     * @param timeNanos the time nanos
     * @param button the button
     * @param x the x coordinate
     * @param y the y coordinate
     * @return a new pointer event
     */
    public static PointerEvent button(long timeNanos, MouseButton button, int x, int y) {
        return new PointerEvent(timeNanos, 0, PointerType.MOUSE, button, x, y, 0.0f, 0.0f);
    }

    /**
     * Creates a pointer event.
     *
     * @param timeNanos the time nanos
     * @param x the x coordinate
     * @param y the y coordinate
     * @param scrollX the scroll x
     * @param scrollY the scroll y
     * @return a new pointer event
     */
    public static PointerEvent scroll(long timeNanos, int x, int y, float scrollX, float scrollY) {
        return new PointerEvent(timeNanos, 0, PointerType.MOUSE, MouseButton.UNKNOWN, x, y, scrollX, scrollY);
    }

    /**
     * Returns the pointer ID.
     *
     * @return the pointer ID
     */
    public int pointerId() {
        return pointerId;
    }

    /**
     * Returns the type.
     *
     * @return the type
     */
    public PointerType type() {
        return type;
    }

    /**
     * Returns the button.
     *
     * @return the button
     */
    public MouseButton button() {
        return button;
    }

    /**
     * Returns the x.
     *
     * @return the x
     */
    public int x() {
        return x;
    }

    /**
     * Returns the y.
     *
     * @return the y
     */
    public int y() {
        return y;
    }

    /**
     * Returns the scroll x.
     *
     * @return the scroll x
     */
    public float scrollX() {
        return scrollX;
    }

    /**
     * Returns the scroll y.
     *
     * @return the scroll y
     */
    public float scrollY() {
        return scrollY;
    }
}
