package io.github.libfdx.graphics.g2d;

import io.github.libfdx.core.FdxException;
import io.github.libfdx.graphics.Texture;

/**
 * Represents a texture region.
 *
 * @author xpenatan
 */
public final class TextureRegion {
    private final Texture texture;
    private final int x;
    private final int y;
    private final int width;
    private final int height;
    private final float u;
    private final float v;
    private final float u2;
    private final float v2;

    /**
     * Creates a texture region.
     *
     * @param texture the texture
     */
    public TextureRegion(Texture texture) {
        this(texture, 0, 0, texture != null ? texture.width() : 0, texture != null ? texture.height() : 0);
    }

    /**
     * Creates a texture region.
     *
     * @param texture the texture
     * @param x the x coordinate
     * @param y the y coordinate
     * @param width the width in pixels
     * @param height the height in pixels
     */
    public TextureRegion(Texture texture, int x, int y, int width, int height) {
        this(texture, x, y, width, height, io.github.libfdx.graphics.TextureOrigin.TOP_LEFT);
    }

    /** Borrows the resolved rendered color with provider-declared origin; rebuild after target resize. */
    public static TextureRegion rendered(io.github.libfdx.graphics.OffscreenTarget target) {
        return rendered(target.color(), target.origin());
    }

    /** Borrows an entire rendered texture. An unknown origin is rejected rather than guessed. */
    public static TextureRegion rendered(Texture texture, io.github.libfdx.graphics.TextureOrigin origin) {
        if (texture == null || !texture.usage().renderAttachment() || !texture.usage().sampled() || texture.sampleCount() != 1) {
            throw new FdxException("Rendered region requires a resolved sampled render attachment");
        }
        if (origin == null || origin == io.github.libfdx.graphics.TextureOrigin.UNKNOWN) {
            throw new FdxException("Rendered region requires an explicit texture origin");
        }
        return new TextureRegion(texture, 0, 0, texture.width(), texture.height(), origin);
    }

    private TextureRegion(Texture texture, int x, int y, int width, int height, io.github.libfdx.graphics.TextureOrigin origin) {
        if (texture == null) {
            throw new FdxException("TextureRegion texture cannot be null");
        }
        if (width <= 0 || height <= 0) {
            throw new FdxException("TextureRegion size must be greater than zero");
        }
        this.texture = texture;
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.u = x / (float) texture.width();
        this.v = origin.v(y / (float) texture.height());
        this.u2 = (x + width) / (float) texture.width();
        this.v2 = origin.v((y + height) / (float) texture.height());
    }

    /** Tile-only sampling view; retains pixel metadata and the borrowed texture. */
    TextureRegion tileSamplingRegion() {
        return new TextureRegion(this);
    }

    private TextureRegion(TextureRegion source) {
        texture = source.texture;
        x = source.x; y = source.y; width = source.width; height = source.height;
        // Keep the complete nearest/linear filter footprint inside this tile.
        // Interpolating the existing UV direction also preserves rendered-texture origins.
        float halfU = (source.u2 - source.u) / width * .5f;
        float halfV = (source.v2 - source.v) / height * .5f;
        u = source.u + halfU; u2 = source.u2 - halfU;
        v = source.v + halfV; v2 = source.v2 - halfV;
    }

    /**
     * Runs the split step.
     *
     * @param texture the texture
     * @param tileWidth the tile width
     * @param tileHeight the tile height
     * @return the split
     */
    public static TextureRegion[][] split(Texture texture, int tileWidth, int tileHeight) {
        if (texture == null) {
            throw new FdxException("Texture cannot be null");
        }
        if (tileWidth <= 0 || tileHeight <= 0) {
            throw new FdxException("Tile size must be greater than zero");
        }
        int columns = texture.width() / tileWidth;
        int rows = texture.height() / tileHeight;
        TextureRegion[][] regions = new TextureRegion[rows][columns];
        for (int row = 0; row < rows; row++) {
            for (int column = 0; column < columns; column++) {
                regions[row][column] = new TextureRegion(texture, column * tileWidth, row * tileHeight,
                        tileWidth, tileHeight);
            }
        }
        return regions;
    }

    /**
     * Returns the texture.
     *
     * @return the texture
     */
    public Texture texture() {
        return texture;
    }

    /**
     * Returns the x.
     *
     * @return the x
     */
    public int x() {
        return x;
    }

    /**
     * Returns the y.
     *
     * @return the y
     */
    public int y() {
        return y;
    }

    /**
     * Returns the width.
     *
     * @return the width
     */
    public int width() {
        return width;
    }

    /**
     * Returns the height.
     *
     * @return the height
     */
    public int height() {
        return height;
    }

    /**
     * Returns the u.
     *
     * @return the u
     */
    public float u() {
        return u;
    }

    /**
     * Returns the v.
     *
     * @return the v
     */
    public float v() {
        return v;
    }

    /**
     * Returns the u2.
     *
     * @return the u2
     */
    public float u2() {
        return u2;
    }

    /**
     * Returns the v2.
     *
     * @return the v2
     */
    public float v2() {
        return v2;
    }
}
