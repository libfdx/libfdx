package io.github.libfdx.runtime.core;

import io.github.libfdx.collections.IntMap;
import io.github.libfdx.collections.IntMapView;
import io.github.libfdx.collections.LongMap;
import io.github.libfdx.collections.LongMapView;

import java.nio.ByteBuffer;

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
    private final IntMap<RasterizedGlyph> glyphs;
    private final LongMap<Integer> kernings;
    private final IntMapView<RasterizedGlyph> glyphView;
    private final LongMapView<Integer> kerningView;

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
            int atlasHeight, ByteBuffer rgba, IntMapView<RasterizedGlyph> glyphs,
            LongMapView<Integer> kernings) {
        this.name = name != null ? name : "font";
        this.nativeSize = nativeSize > 0.0f ? nativeSize : 16.0f;
        this.lineHeight = lineHeight > 0.0f ? lineHeight : this.nativeSize;
        this.baseLine = baseLine > 0.0f ? baseLine : this.lineHeight;
        this.atlasWidth = Math.max(1, atlasWidth);
        this.atlasHeight = Math.max(1, atlasHeight);
        this.rgba = rgba != null ? rgba.slice() : ByteBuffer.allocateDirect(0);
        this.glyphs = glyphs != null ? new IntMap<RasterizedGlyph>(glyphs) : new IntMap<RasterizedGlyph>(0);
        this.kernings = kernings != null ? new LongMap<Integer>(kernings) : new LongMap<Integer>(0);
        this.glyphView = this.glyphs.view();
        this.kerningView = this.kernings.view();
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
    public IntMapView<RasterizedGlyph> glyphs() { return glyphView; }
    /**
     * Returns the kernings.
     *
     * @return the kernings
     */
    public LongMapView<Integer> kernings() { return kerningView; }
}
