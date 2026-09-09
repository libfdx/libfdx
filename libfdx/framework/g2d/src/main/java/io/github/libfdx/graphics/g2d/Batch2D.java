package io.github.libfdx.graphics.g2d;

import io.github.libfdx.core.Disposable;
import io.github.libfdx.graphics.LoadOp;
import io.github.libfdx.graphics.RenderPass;
import io.github.libfdx.graphics.Texture;

/**
 * Defines the contract for batch2 d implementations.
 *
 * @author xpenatan
 */
public interface Batch2D extends Disposable {
    /**
     * Begins the operation.
     */
    void begin();

    /**
     * Begins the operation.
     *
     * @param loadOp the load op
     */
    void begin(LoadOp loadOp);

    /**
     * Begins the operation.
     *
     * @param pass the pass
     */
    void begin(RenderPass pass);

    /**
     * Sets the color and returns this batch2 d.
     *
     * @param red the red
     * @param green the green
     * @param blue the blue
     * @param alpha the alpha
     * @return this batch2 d for chaining
     */
    Batch2D color(float red, float green, float blue, float alpha);

    /**
     * Sets the viewport and returns this batch2 d.
     *
     * @param width the width in pixels
     * @param height the height in pixels
     * @return this batch2 d for chaining
     */
    Batch2D viewport(int width, int height);

    /**
     * Draws the current content.
     *
     * @param texture the texture
     * @param x the x coordinate
     * @param y the y coordinate
     * @param width the width in pixels
     * @param height the height in pixels
     */
    void draw(Texture texture, float x, float y, float width, float height);

    /**
     * Draws the current content.
     *
     * @param texture the texture
     * @param x the x coordinate
     * @param y the y coordinate
     * @param width the width in pixels
     * @param height the height in pixels
     * @param originX the origin x
     * @param originY the origin y
     * @param rotationDegrees the rotation degrees
     */
    void draw(Texture texture, float x, float y, float width, float height,
            float originX, float originY, float rotationDegrees);

    /**
     * Draws a source rectangle from a texture.
     *
     * @param texture the texture
     * @param sourceX the source x coordinate in texels
     * @param sourceY the source y coordinate in texels
     * @param sourceWidth the source width in texels
     * @param sourceHeight the source height in texels
     * @param x the x coordinate
     * @param y the y coordinate
     * @param width the width in pixels
     * @param height the height in pixels
     */
    void draw(Texture texture, int sourceX, int sourceY, int sourceWidth, int sourceHeight,
            float x, float y, float width, float height);

    /**
     * Draws the current content.
     *
     * @param region the region
     * @param x the x coordinate
     * @param y the y coordinate
     * @param width the width in pixels
     * @param height the height in pixels
     */
    void draw(TextureRegion region, float x, float y, float width, float height);

    /**
     * Draws the current content.
     *
     * @param region the region
     * @param x the x coordinate
     * @param y the y coordinate
     * @param width the width in pixels
     * @param height the height in pixels
     * @param originX the origin x
     * @param originY the origin y
     * @param rotationDegrees the rotation degrees
     */
    void draw(TextureRegion region, float x, float y, float width, float height,
            float originX, float originY, float rotationDegrees);

    /** Draws with image-axis transforms from {@link io.github.libfdx.maps.TileTransform}. */
    default void draw(TextureRegion region, float x, float y, float width, float height, int transform) {
        draw(region, x, y, width, height, 0, 0, 0, transform);
    }

    /**
     * Applies image-axis transforms inside the rectangle before its ordinary
     * rotation. Implementations without transformed UV support reject nonzero
     * transforms; existing untransformed implementations remain compatible.
     */
    default void draw(TextureRegion region, float x, float y, float width, float height,
            float originX, float originY, float rotationDegrees, int transform) {
        if (transform != 0) { throw new io.github.libfdx.core.FdxException("Batch does not support tile transforms"); }
        draw(region, x, y, width, height, originX, originY, rotationDegrees);
    }

    /**
     * Draws the current content.
     *
     * @param region the region
     * @param centerX the center x
     * @param centerY the center y
     * @param count the count
     * @param width the width in pixels
     * @param height the height in pixels
     * @param originX the origin x
     * @param originY the origin y
     * @param rotationDegrees the rotation degrees
     */
    void draw(TextureRegion region, float[] centerX, float[] centerY, int count,
            float width, float height, float originX, float originY, float rotationDegrees);

    /**
     * Ends the operation.
     */
    void end();
}
