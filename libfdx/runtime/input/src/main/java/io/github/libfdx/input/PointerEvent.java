package io.github.libfdx.input;

public final class PointerEvent extends InputEvent {
    private final int pointerId;
    private final PointerType type;
    private final MouseButton button;
    private final int x;
    private final int y;
    private final float scrollX;
    private final float scrollY;

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

    public static PointerEvent pointer(long timeNanos, int x, int y) {
        return new PointerEvent(timeNanos, 0, PointerType.MOUSE, MouseButton.UNKNOWN, x, y, 0.0f, 0.0f);
    }

    public static PointerEvent button(long timeNanos, MouseButton button, int x, int y) {
        return new PointerEvent(timeNanos, 0, PointerType.MOUSE, button, x, y, 0.0f, 0.0f);
    }

    public static PointerEvent scroll(long timeNanos, int x, int y, float scrollX, float scrollY) {
        return new PointerEvent(timeNanos, 0, PointerType.MOUSE, MouseButton.UNKNOWN, x, y, scrollX, scrollY);
    }

    public int pointerId() {
        return pointerId;
    }

    public PointerType type() {
        return type;
    }

    public MouseButton button() {
        return button;
    }

    public int x() {
        return x;
    }

    public int y() {
        return y;
    }

    public float scrollX() {
        return scrollX;
    }

    public float scrollY() {
        return scrollY;
    }
}
