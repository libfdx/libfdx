package io.github.libfdx.runtime.core;

public final class RasterizedGlyph {
    private final int codePoint;
    private final int x;
    private final int y;
    private final int width;
    private final int height;
    private final float xOffset;
    private final float yOffset;
    private final float xAdvance;

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

    public int codePoint() { return codePoint; }
    public int x() { return x; }
    public int y() { return y; }
    public int width() { return width; }
    public int height() { return height; }
    public float xOffset() { return xOffset; }
    public float yOffset() { return yOffset; }
    public float xAdvance() { return xAdvance; }
}