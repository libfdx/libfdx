package io.github.libfdx.ui;

/**
 * Defines the contract for ui draw context implementations.
 *
 * @author xpenatan
 */
public interface UiDrawContext {
    /**
     * Runs the rect step.
     *
     * @param bounds the bounds
     * @param color the color
     */
    void rect(UiRect bounds, UiColor color);

    /**
     * Runs the text step.
     *
     * @param text the text
     * @param bounds the bounds
     * @param style the style
     */
    void text(String text, UiRect bounds, UiTextStyle style);
}
