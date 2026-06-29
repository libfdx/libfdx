package io.github.libfdx.input;

/**
 * Represents a key event.
 *
 * @author xpenatan
 */
public final class KeyEvent extends InputEvent {
    private final Key key;
    private final boolean repeat;

    /**
     * Creates a key event.
     *
     * @param timeNanos the time nanos
     * @param key the key
     * @param repeat the repeat
     */
    public KeyEvent(long timeNanos, Key key, boolean repeat) {
        super(timeNanos);
        this.key = key != null ? key : Key.UNKNOWN;
        this.repeat = repeat;
    }

    /**
     * Returns the key.
     *
     * @return the key
     */
    public Key key() {
        return key;
    }

    /**
     * Returns the repeat.
     *
     * @return true if repeat succeeds or is active; false otherwise
     */
    public boolean repeat() {
        return repeat;
    }
}
