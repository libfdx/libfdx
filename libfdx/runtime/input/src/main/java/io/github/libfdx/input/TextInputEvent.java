package io.github.libfdx.input;

public final class TextInputEvent extends InputEvent {
    private final String text;
    private final boolean composition;

    public TextInputEvent(long timeNanos, String text, boolean composition) {
        super(timeNanos);
        this.text = text != null ? text : "";
        this.composition = composition;
    }

    public String text() {
        return text;
    }

    public boolean composition() {
        return composition;
    }
}
