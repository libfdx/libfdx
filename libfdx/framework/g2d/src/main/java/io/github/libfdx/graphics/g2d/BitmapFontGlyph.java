package io.github.libfdx.graphics.g2d;

/**
 * Represents a bitmap font glyph.
 *
 * @author xpenatan
 */
public final class BitmapFontGlyph {
    private final int codePoint;
    private final TextureRegion region;
    private final float xOffset;
    private final float yOffset;
    private final float xAdvance;

    /**
     * Creates a bitmap font glyph.
     *
     * @param codePoint the code point
     * @param region the region
     * @param xOffset the x offset
     * @param yOffset the y offset
     * @param xAdvance the x advance
     */
    public BitmapFontGlyph(int codePoint, TextureRegion region, float xOffset, float yOffset, float xAdvance) {
        this.codePoint = codePoint;
        this.region = region;
        this.xOffset = xOffset;
        this.yOffset = yOffset;
        this.xAdvance = xAdvance;
    }

    /**
     * Returns the code point.
     *
     * @return the code point
     */
    public int codePoint() {
        return codePoint;
    }

    /**
     * Returns the region.
     *
     * @return the region
     */
    public TextureRegion region() {
        return region;
    }

    /**
     * Returns the x offset.
     *
     * @return the x offset
     */
    public float xOffset() {
        return xOffset;
    }

    /**
     * Returns the y offset.
     *
     * @return the y offset
     */
    public float yOffset() {
        return yOffset;
    }

    /**
     * Returns the x advance.
     *
     * @return the x advance
     */
    public float xAdvance() {
        return xAdvance;
    }
}
