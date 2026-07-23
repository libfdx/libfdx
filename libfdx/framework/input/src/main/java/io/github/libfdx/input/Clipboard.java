package io.github.libfdx.input;

/**
 * Provides access to the platform text clipboard.
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
