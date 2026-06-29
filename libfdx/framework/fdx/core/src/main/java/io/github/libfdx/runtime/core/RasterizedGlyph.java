package io.github.libfdx.runtime.core;

/**
 * Represents a rasterized glyph.
 *
 * @author xpenatan
 */
public final class RasterizedGlyph {
    private final int codePoint;
    private final int x;
    private final int y;
    private final int width;
    private final int height;
    private final float xOffset;
    private final float yOffset;
    private final float xAdvance;

    /**
     * Creates a rasterized glyph.
     *
     * @param codePoint the code point
     * @param x the x coordinate
     * @param y the y coordinate
     * @param width the width in pixels
     * @param height the height in pixels
     * @param xOffset the x offset
     * @param yOffset the y offset
     * @param xAdvance the x advance
     */
    public RasterizedGlyph(int codePoint, int x, int y, int width, int height, float xOffset, float yOffset,
            float xAdvance) {
        this.codePoint = codePoint;
        this.x = x;
        this.y = y;
        this.width = Math.max(1, width);
        this.height = Math.max(1, height);
        this.xOffset = xOffset;
        this.yOffset = yOffset;
        this.xAdvance = xAdvance;
    }

    /**
     * Returns the code point.
     *
     * @return the code point
     */
    public int codePoint() { return codePoint; }
    /**
     * Returns the x.
     *
     * @return the x
     */
    public int x() { return x; }
    /**
     * Returns the y.
     *
     * @return the y
     */
    public int y() { return y; }
    /**
     * Returns the width.
     *
     * @return the width
     */
    public int width() { return width; }
    /**
     * Returns the height.
     *
     * @return the height
     */
    public int height() { return height; }
    /**
     * Returns the x offset.
     *
     * @return the x offset
     */
    public float xOffset() { return xOffset; }
    /**
     * Returns the y offset.
     *
     * @return the y offset
     */
    public float yOffset() { return yOffset; }
    /**
     * Returns the x advance.
     *
     * @return the x advance
     */
    public float xAdvance() { return xAdvance; }
}