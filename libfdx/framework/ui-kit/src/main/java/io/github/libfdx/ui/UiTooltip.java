package io.github.libfdx.ui;

/**
 * Represents an ui tooltip.
 *
 * @author xpenatan
 */
public final class UiTooltip {
    private final String text;
    private final int delayMillis;
    private final UiAlign align;

    private UiTooltip(String text, int delayMillis, UiAlign align) {
        this.text = text;
        this.delayMillis = Math.max(0, delayMillis);
        this.align = align != null ? align : UiAlign.CENTER;
    }

    /**
     * Creates an UI tooltip.
     *
     * @param text the text
     * @return a new UI tooltip
     */
    public static UiTooltip text(String text) {
        return new UiTooltip(text, 350, UiAlign.CENTER);
    }

    /**
     * Sets the delay millis and returns this UI tooltip.
     *
     * @param delayMillis the delay millis
     * @return this UI tooltip for chaining
     */
    public UiTooltip delayMillis(int delayMillis) {
        return new UiTooltip(text, delayMillis, align);
    }

    /**
     * Sets the align and returns this UI tooltip.
     *
     * @param align the align
     * @return this UI tooltip for chaining
     */
    public UiTooltip align(UiAlign align) {
        return new UiTooltip(text, delayMillis, align);
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
     * Returns the delay millis.
     *
     * @return the delay millis
     */
    public int delayMillis() {
        return delayMillis;
    }

    /**
     * Returns the align.
     *
     * @return the align
     */
    public UiAlign align() {
        return align;
    }
}
