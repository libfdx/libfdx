package io.github.libfdx.input;

public final class TouchEvent extends InputEvent {
    private final TouchPoint point;

    public TouchEvent(long timeNanos, TouchPoint point) {
        super(timeNanos);
        this.point = point;
    }

    public TouchPoint point() {
        return point;
    }
}
