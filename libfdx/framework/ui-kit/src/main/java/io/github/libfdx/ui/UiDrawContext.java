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
     * Draws a line in UI-root coordinates.
     *
     * @param x1 the first horizontal coordinate
     * @param y1 the first vertical coordinate
     * @param x2 the second horizontal coordinate
     * @param y2 the second vertical coordinate
     * @param width the line width in UI pixels
     * @param color the line color
     */
    void line(float x1, float y1, float x2, float y2, float width, UiColor color);

    /**
     * Draws a retained path in UI-root coordinates.
     *
     * @param path the path
     * @param width the stroke width in UI pixels
     * @param color the stroke color
     */
    void path(UiPath path, float width, UiColor color);

    /**
     * Runs the text step.
     *
     * @param text the text
     * @param bounds the bounds
     * @param style the style
     */
    void text(String text, UiRect bounds, UiTextStyle style);
}
