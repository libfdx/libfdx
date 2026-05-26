package io.github.libfdx.graphics.g2d;

public final class BitmapFontGlyph {
    private final int codePoint;
    private final TextureRegion region;
    private final float xOffset;
    private final float yOffset;
    private final float xAdvance;

    public BitmapFontGlyph(int codePoint, TextureRegion region, float xOffset, float yOffset, float xAdvance) {
        this.codePoint = codePoint;
        this.region = region;
        this.xOffset = xOffset;
        this.yOffset = yOffset;
        this.xAdvance = xAdvance;
    }

    public int codePoint() {
        return codePoint;
    }

    public TextureRegion region() {
        return region;
    }

    public float xOffset() {
        return xOffset;
    }

    public float yOffset() {
        return yOffset;
    }

    public float xAdvance() {
        return xAdvance;
    }
}
