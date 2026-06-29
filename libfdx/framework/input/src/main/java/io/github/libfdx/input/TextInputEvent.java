package io.github.libfdx.input;

/**
 * Represents a text input event.
 *
 * @author xpenatan
 */
public final class TextInputEvent extends InputEvent {
    private final String text;
    private final boolean composition;

    /**
     * Creates a text input event.
     *
     * @param timeNanos the time nanos
     * @param text the text
     * @param composition the composition
     */
    public TextInputEvent(long timeNanos, String text, boolean composition) {
        super(timeNanos);
        this.text = text != null ? text : "";
        this.composition = composition;
    }

    /**
     * Returns the text.
     *
     * @return the text
     */
    public String text() {
        return text;
    }

    /**
     * Returns the composition.
     *
     * @return true if composition succeeds or is active; false otherwise
     */
    public boolean composition() {
        return composition;
    }
}
