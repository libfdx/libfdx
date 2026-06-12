package io.github.libfdx.runtime.core;

import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Represents a rasterized font.
 *
 * @author xpenatan
 */
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

    /**
     * Creates a rasterized font.
     *
     * @param name the name
     * @param nativeSize the native size
     * @param lineHeight the line height
     * @param baseLine the base line
     * @param atlasWidth the atlas width
     * @param atlasHeight the atlas height
     * @param rgba the RGBA
     * @param glyphs the glyphs
     * @param kernings the kernings
     */
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

    /**
     * Returns the name.
     *
     * @return the name
     */
    public String name() { return name; }
    /**
     * Returns the native size.
     *
     * @return the native size
     */
    public float nativeSize() { return nativeSize; }
    /**
     * Returns the line height.
     *
     * @return the line height
     */
    public float lineHeight() { return lineHeight; }
    /**
     * Returns the base line.
     *
     * @return the base line
     */
    public float baseLine() { return baseLine; }
    /**
     * Returns the atlas width.
     *
     * @return the atlas width
     */
    public int atlasWidth() { return atlasWidth; }
    /**
     * Returns the atlas height.
     *
     * @return the atlas height
     */
    public int atlasHeight() { return atlasHeight; }
    /**
     * Returns the RGBA.
     *
     * @return the RGBA
     */
    public ByteBuffer rgba() {
        ByteBuffer copy = rgba.duplicate();
        copy.clear();
        return copy;
    }
    /**
     * Returns the glyphs.
     *
     * @return the glyphs
     */
    public Map<Integer, RasterizedGlyph> glyphs() { return glyphs; }
    /**
     * Returns the kernings.
     *
     * @return the kernings
     */
    public Map<Long, Integer> kernings() { return kernings; }
}