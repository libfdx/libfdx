package io.github.libfdx.ui;

/**
 * Defines the contract for ui draw function implementations.
 *
 * @author xpenatan
 */
public interface UiDrawFunction {
    /**
     * Draws the current content.
     *
     * @param draw the draw
     * @param bounds the bounds
     */
    void draw(UiDrawContext draw, UiRect bounds);
}
