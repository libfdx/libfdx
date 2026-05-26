package io.github.libfdx.input;

public abstract class InputEvent {
    private final long timeNanos;

    protected InputEvent(long timeNanos) {
        this.timeNanos = timeNanos;
    }

    public long timeNanos() {
        return timeNanos;
    }
}
