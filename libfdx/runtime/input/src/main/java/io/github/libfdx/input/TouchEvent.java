package io.github.libfdx.input;

/**
 * Represents a touch event.
 *
 * @author xpenatan
 */
public final class TouchEvent extends InputEvent {
    private final TouchPoint point;

    /**
     * Creates a touch event.
     *
     * @param timeNanos the time nanos
     * @param point the point
     */
    public TouchEvent(long timeNanos, TouchPoint point) {
        super(timeNanos);
        this.point = point;
    }

    /**
     * Returns the point.
     *
     * @return the point
     */
    public TouchPoint point() {
        return point;
    }
}
