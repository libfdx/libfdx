package io.github.libfdx.input;

/**
 * Represents an input event.
 *
 * @author xpenatan
 */
public abstract class InputEvent {
    private final long timeNanos;

    protected InputEvent(long timeNanos) {
        this.timeNanos = timeNanos;
    }

    /**
     * Returns the time nanos.
     *
     * @return the time nanos
     */
    public long timeNanos() {
        return timeNanos;
    }
}
