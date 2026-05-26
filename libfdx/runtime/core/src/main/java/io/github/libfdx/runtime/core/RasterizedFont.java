package io.github.libfdx.runtime.core;

import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class RasterizedFont {
    private final String name;
    private final float nativeSize;
    private final float lineHeight;
    private final float baseLine;
    private final int atlasWidth;
    private final int atlasHeight;
    private final ByteBuffer rgba;
    private final Map<Integer, RasterizedGlyph> glyphs;
    private final Map<Long, Integer> kernings;

    public RasterizedFont(String name, float nativeSize, float lineHeight, float baseLine, int atlasWidth,
            int atlasHeight, ByteBuffer rgba, Map<Integer, RasterizedGlyph> glyphs, Map<Long, Integer> kernings) {
        this.name = name != null ? name : "font";
        this.nativeSize = nativeSize > 0.0f ? nativeSize : 16.0f;
        this.lineHeight = lineHeight > 0.0f ? lineHeight : this.nativeSize;
        this.baseLine = baseLine > 0.0f ? baseLine : this.lineHeight;
        this.atlasWidth = Math.max(1, atlasWidth);
        this.atlasHeight = Math.max(1, atlasHeight);
        this.rgba = rgba != null ? rgba.slice() : ByteBuffer.allocateDirect(0);
        this.glyphs = Collections.unmodifiableMap(new LinkedHashMap<Integer, RasterizedGlyph>(glyphs));
        this.kernings = Collections.unmodifiableMap(new LinkedHashMap<Long, Integer>(kernings));
    }

    public String name() { return name; }
    public float nativeSize() { return nativeSize; }
    public float lineHeight() { return lineHeight; }
    public float baseLine() { return baseLine; }
    public int atlasWidth() { return atlasWidth; }
    public int atlasHeight() { return atlasHeight; }
    public ByteBuffer rgba() {
        ByteBuffer copy = rgba.duplicate();
        copy.clear();
        return copy;
    }
    public Map<Integer, RasterizedGlyph> glyphs() { return glyphs; }
    public Map<Long, Integer> kernings() { return kernings; }
}