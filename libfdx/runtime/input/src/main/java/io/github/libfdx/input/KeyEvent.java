package io.github.libfdx.input;

public final class KeyEvent extends InputEvent {
    private final Key key;
    private final boolean repeat;

    public KeyEvent(long timeNanos, Key key, boolean repeat) {
        super(timeNanos);
        this.key = key != null ? key : Key.UNKNOWN;
        this.repeat = repeat;
    }

    public Key key() {
        return key;
    }

    public boolean repeat() {
        return repeat;
    }
}
