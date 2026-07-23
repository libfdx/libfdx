package io.github.libfdx.input;

/**
 * Stores clipboard text in memory when a backend does not expose a system clipboard.
 *
 * @author xpenatan
 */
public final class MemoryClipboard implements Clipboard {
    private String text = "";

    /**
     * {@inheritDoc}
     */
    @Override
    public String getText() {
        return text;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setText(String text) {
        this.text = text != null ? text : "";
    }
}
