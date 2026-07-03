package io.github.libfdx.ui;

import io.github.libfdx.graphics.g2d.TextureRegion;

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
     * Runs the rect step.
     *
     * @param x the x position
     * @param y the y position
     * @param width the width
     * @param height the height
     * @param color the color
     */
    default void rect(float x, float y, float width, float height, UiColor color) {
        rect(new UiRect(x, y, width, height), color);
    }

    /**
     * Runs the image step.
     *
     * @param region the texture region
     * @param bounds the bounds
     */
    default void image(TextureRegion region, UiRect bounds) {
        image(region, bounds, UiColor.WHITE);
    }

    /**
     * Runs the image step.
     *
     * @param region the texture region
     * @param bounds the bounds
     * @param color the tint color
     */
    default void image(TextureRegion region, UiRect bounds, UiColor color) {
    }

    /**
     * Runs the image step.
     *
     * @param region the texture region
     * @param x the x position
     * @param y the y position
     * @param width the width
     * @param height the height
     * @param color the tint color
     */
    default void image(TextureRegion region, float x, float y, float width, float height, UiColor color) {
        image(region, new UiRect(x, y, width, height), color);
    }

    /**
     * Runs the text step.
     *
     * @param text the text
     * @param bounds the bounds
     * @param style the style
     */
    void text(String text, UiRect bounds, UiTextStyle style);
}
