package io.github.libfdx.input;

/**
 * Provides access to the backend text clipboard.
 *
 * <p>Backends bridge the system clipboard where the platform exposes a suitable
 * API. A backend without that capability may provide an in-process clipboard
 * so UI copy/paste remains deterministic.</p>
 *
 * @author xpenatan
 */
public interface Clipboard {
    /**
     * Returns the current clipboard text.
     *
     * @return the clipboard text, never {@code null}
     */
    String getText();

    /**
     * Replaces the current clipboard text.
     *
     * @param text the text to store
     */
    void setText(String text);
}
