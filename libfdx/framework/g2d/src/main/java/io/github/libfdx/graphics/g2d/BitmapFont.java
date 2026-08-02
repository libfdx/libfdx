package io.github.libfdx.graphics.g2d;

import io.github.libfdx.collections.Array;
import io.github.libfdx.collections.ArrayView;
import io.github.libfdx.collections.FloatArray;
import io.github.libfdx.collections.IntMap;
import io.github.libfdx.collections.IntMapView;
import io.github.libfdx.collections.IntSet;
import io.github.libfdx.collections.LongMap;
import io.github.libfdx.collections.LongMapView;
import io.github.libfdx.core.Disposable;
import io.github.libfdx.graphics.Texture;
import java.util.Arrays;

/**
 * Represents a bitmap font.
 *
 * @author xpenatan
 */
public final class BitmapFont implements Disposable {
    private static final int ASCII_CACHE_SIZE = 128;
    private static final int NO_KERNING = Integer.MIN_VALUE;

    private final String name;
    private final float nativeSize;
    private final float lineHeight;
    private final float baseLine;
    private final IntMap<BitmapFontGlyph> glyphs;
    private final LongMap<Integer> kernings;
    private final Array<Texture> pages;
    private final ArrayView<Texture> pageView;
    private final BitmapFontGlyph fallbackGlyph;
    private final BitmapFontGlyph[] asciiGlyphs;
    private final int[] asciiKernings;
    private final boolean ownsPages;
    private boolean disposed;

    /**
     * Creates a bitmap font.
     *
     * @param name the name
     * @param nativeSize the native size
     * @param lineHeight the line height
     * @param baseLine the base line
     * @param glyphs the glyphs
     * @param kernings the kernings
     * @param pages the pages
     * @param ownsPages the owns pages
     */
    public BitmapFont(String name, float nativeSize, float lineHeight, float baseLine,
            IntMapView<BitmapFontGlyph> glyphs, LongMapView<Integer> kernings, ArrayView<Texture> pages,
            boolean ownsPages) {
        this.name = name != null ? name : "bitmap";
        this.nativeSize = nativeSize > 0.0f ? nativeSize : lineHeight > 0.0f ? lineHeight : 16.0f;
        this.lineHeight = lineHeight > 0.0f ? lineHeight : this.nativeSize;
        this.baseLine = baseLine > 0.0f ? baseLine : this.lineHeight;
        this.glyphs = glyphs != null ? new IntMap<BitmapFontGlyph>(glyphs) : new IntMap<BitmapFontGlyph>(0);
        this.kernings = kernings != null ? new LongMap<Integer>(kernings) : new LongMap<Integer>(0);
        this.pages = pages != null ? new Array<Texture>(pages) : new Array<Texture>(0);
        this.pageView = this.pages.view();
        this.fallbackGlyph = this.glyphs.get('?');
        this.asciiGlyphs = new BitmapFontGlyph[ASCII_CACHE_SIZE];
        for (IntMap.Entry<BitmapFontGlyph> entry : this.glyphs.entries()) {
            int codePoint = entry.key();
            if (codePoint >= 0 && codePoint < ASCII_CACHE_SIZE) {
                asciiGlyphs[codePoint] = entry.value();
            }
        }
        this.asciiKernings = new int[ASCII_CACHE_SIZE * ASCII_CACHE_SIZE];
        Arrays.fill(asciiKernings, NO_KERNING);
        for (LongMap.Entry<Integer> entry : this.kernings.entries()) {
            long key = entry.key();
            int first = (int) (key >> 32);
            int second = (int) key;
            if (first >= 0 && first < ASCII_CACHE_SIZE && second >= 0 && second < ASCII_CACHE_SIZE) {
                asciiKernings[first * ASCII_CACHE_SIZE + second] = entry.value().intValue();
            }
        }
        this.ownsPages = ownsPages;
    }

    /**
     * Creates a bitmap font.
     *
     * @param texture the texture
     * @param characters the characters
     * @param glyphWidth the glyph width
     * @param glyphHeight the glyph height
     * @return a new bitmap font
     */
    public static BitmapFont fromGrid(Texture texture, String characters, int glyphWidth, int glyphHeight) {
        String text = characters != null ? characters : "";
        IntMap<BitmapFontGlyph> glyphs = new IntMap<BitmapFontGlyph>();
        int columns = Math.max(1, texture.width() / Math.max(1, glyphWidth));
        IntSet seen = new IntSet();
        int glyphIndex = 0;
        for (int i = 0; i < text.length();) {
            int codePoint = text.codePointAt(i);
            if (!seen.add(codePoint)) {
                glyphIndex++;
                i += Character.charCount(codePoint);
                continue;
            }
            int column = glyphIndex % columns;
            int row = glyphIndex / columns;
            int x = column * glyphWidth;
            int y = row * glyphHeight;
            if (x + glyphWidth <= texture.width() && y + glyphHeight <= texture.height()) {
                glyphs.put(codePoint, new BitmapFontGlyph(codePoint,
                        new TextureRegion(texture, x, y, glyphWidth, glyphHeight), 0.0f, 0.0f, glyphWidth));
            }
            glyphIndex++;
            i += Character.charCount(codePoint);
        }
        Array<Texture> pages = new Array<Texture>();
        pages.add(texture);
        return new BitmapFont("grid", glyphHeight, glyphHeight, glyphHeight, glyphs,
                new LongMap<Integer>(0), pages, false);
    }

    /**
     * Returns the name.
     *
     * @return the name
     */
    public String name() {
        return name;
    }

    /**
     * Returns the native size.
     *
     * @return the native size
     */
    public float nativeSize() {
        return nativeSize;
    }

    /**
     * Returns the line height.
     *
     * @return the line height
     */
    public float lineHeight() {
        return lineHeight;
    }

    /**
     * Returns the base line.
     *
     * @return the base line
     */
    public float baseLine() {
        return baseLine;
    }

    /**
     * Runs the glyph step.
     *
     * @param codePoint the code point
     * @return the glyph
     */
    public BitmapFontGlyph glyph(int codePoint) {
        BitmapFontGlyph glyph = codePoint >= 0 && codePoint < ASCII_CACHE_SIZE
                ? asciiGlyphs[codePoint]
                : null;
        if (glyph != null) {
            return glyph;
        }
        glyph = glyphs.get(codePoint);
        return glyph != null ? glyph : fallbackGlyph;
    }

    /**
     * Returns whether this instance has glyph.
     *
     * @param codePoint the code point
     * @return true if this instance has glyph; false otherwise
     */
    public boolean hasGlyph(int codePoint) {
        if (codePoint >= 0 && codePoint < ASCII_CACHE_SIZE && asciiGlyphs[codePoint] != null) {
            return true;
        }
        return glyphs.containsKey(codePoint);
    }

    /**
     * Runs the kerning step.
     *
     * @param first the first
     * @param second the second
     * @return the kerning
     */
    public int kerning(int first, int second) {
        if (first >= 0 && first < ASCII_CACHE_SIZE && second >= 0 && second < ASCII_CACHE_SIZE) {
            int value = asciiKernings[first * ASCII_CACHE_SIZE + second];
            return value != NO_KERNING ? value : 0;
        }
        Integer value = kernings.get(kerningKey(first, second));
        return value != null ? value.intValue() : 0;
    }

    /**
     * Runs the scale step.
     *
     * @param size the size
     * @return the scale
     */
    public float scale(float size) {
        return (size > 0.0f ? size : nativeSize) / nativeSize;
    }

    /**
     * Runs the line height step.
     *
     * @param size the size
     * @return the line height
     */
    public float lineHeight(float size) {
        return lineHeight * scale(size);
    }

    /**
     * Runs the width step.
     *
     * @param text the text
     * @param size the size
     * @return the width
     */
    public float width(String text, float size) {
        if (text == null || text.length() == 0) {
            return 0.0f;
        }
        float scale = scale(size);
        float width = 0.0f;
        int previous = -1;
        for (int i = 0; i < text.length();) {
            int codePoint = text.codePointAt(i);
            BitmapFontGlyph glyph = glyph(codePoint);
            if (glyph == null) {
                width += nativeSize * 0.5f * scale;
                previous = -1;
            } else {
                if (previous >= 0) {
                    width += kerning(previous, codePoint) * scale;
                }
                width += glyph.xAdvance() * scale;
                previous = codePoint;
            }
            i += Character.charCount(codePoint);
        }
        return width;
    }

    /**
     * Runs the layout step.
     *
     * @param text the text
     * @param size the size
     * @param maxWidth the max width
     * @param wrap the wrap
     * @param ellipsis the ellipsis
     * @return the layout
     */
    public BitmapFontLayout layout(String text, float size, float maxWidth, boolean wrap, boolean ellipsis) {
        Array<String> lines = new Array<String>();
        FloatArray widths = new FloatArray();
        String value = text != null ? text : "";
        int lineStart = 0;
        for (int i = 0; i <= value.length(); i++) {
            if (i == value.length() || value.charAt(i) == '\n') {
                appendWrappedLine(value.substring(lineStart, i), size, maxWidth, wrap, ellipsis, lines, widths);
                lineStart = i + 1;
            }
        }
        if (lines.isEmpty()) {
            lines.add("");
            widths.add(0.0f);
        }
        float max = 0.0f;
        for (int i = 0; i < widths.size(); i++) {
            max = Math.max(max, widths.get(i));
        }
        float actualLineHeight = lineHeight(size);
        return new BitmapFontLayout(lines, widths, max, actualLineHeight * lines.size(), actualLineHeight);
    }

    /**
     * Returns the pages.
     *
     * @return the pages
     */
    public ArrayView<Texture> pages() {
        return pageView;
    }

    /**
     * Releases resources held by this instance.
     */
    @Override
    public void dispose() {
        if (disposed) {
            return;
        }
        disposed = true;
        if (ownsPages) {
            for (int i = 0; i < pages.size(); i++) {
                Texture texture = pages.get(i);
                if (texture != null && !texture.isDisposed()) {
                    texture.dispose();
                }
            }
        }
    }

    /**
     * Returns whether this instance has already been disposed.
     *
     * @return true if disposed is enabled or true; false otherwise
     */
    @Override
    public boolean isDisposed() {
        return disposed;
    }

    static long kerningKey(int first, int second) {
        return ((long) first << 32) ^ (second & 0xffffffffL);
    }

    private void appendWrappedLine(String value, float size, float maxWidth, boolean wrap, boolean ellipsis,
            Array<String> lines, FloatArray widths) {
        if (!wrap || maxWidth <= 0.0f || width(value, size) <= maxWidth) {
            appendLine(truncate(value, size, maxWidth, ellipsis), size, lines, widths);
            return;
        }
        int start = 0;
        while (start < value.length()) {
            int end = start + Character.charCount(value.codePointAt(start));
            int best = -1;
            while (end <= value.length()) {
                String candidate = value.substring(start, end);
                float candidateWidth = width(candidate, size);
                if (candidateWidth > maxWidth) {
                    break;
                }
                int previous = value.codePointBefore(end);
                if (end == value.length() || Character.isWhitespace(previous)) {
                    best = end;
                }
                if (end == value.length()) {
                    break;
                }
                end += Character.charCount(value.codePointAt(end));
            }
            if (best <= start) {
                best = end > value.length() ? value.length() : end;
                if (width(value.substring(start, best), size) > maxWidth && best > start) {
                    best = value.offsetByCodePoints(best, -1);
                }
                if (best <= start) {
                    best = start + Character.charCount(value.codePointAt(start));
                }
            }
            String line = value.substring(start, best).trim();
            appendLine(line, size, lines, widths);
            start = best;
            while (start < value.length()) {
                int codePoint = value.codePointAt(start);
                if (!Character.isWhitespace(codePoint)) {
                    break;
                }
                start += Character.charCount(codePoint);
            }
        }
    }

    private String truncate(String value, float size, float maxWidth, boolean ellipsis) {
        if (!ellipsis || maxWidth <= 0.0f || width(value, size) <= maxWidth) {
            return value;
        }
        String marker = "...";
        float markerWidth = width(marker, size);
        int end = value.length();
        while (end > 0 && width(value.substring(0, end), size) + markerWidth > maxWidth) {
            end = value.offsetByCodePoints(end, -1);
        }
        return value.substring(0, end) + marker;
    }

    private void appendLine(String line, float size, Array<String> lines, FloatArray widths) {
        lines.add(line);
        widths.add(width(line, size));
    }
}
