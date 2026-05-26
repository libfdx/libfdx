package io.github.libfdx.graphics.g2d;

import io.github.libfdx.core.Disposable;
import io.github.libfdx.graphics.Texture;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class BitmapFont implements Disposable {
    private static final int ASCII_CACHE_SIZE = 128;
    private static final int NO_KERNING = Integer.MIN_VALUE;

    private final String name;
    private final float nativeSize;
    private final float lineHeight;
    private final float baseLine;
    private final Map<Integer, BitmapFontGlyph> glyphs;
    private final Map<Long, Integer> kernings;
    private final List<Texture> pages;
    private final BitmapFontGlyph fallbackGlyph;
    private final BitmapFontGlyph[] asciiGlyphs;
    private final int[] asciiKernings;
    private final boolean ownsPages;
    private boolean disposed;

    public BitmapFont(String name, float nativeSize, float lineHeight, float baseLine,
            Map<Integer, BitmapFontGlyph> glyphs, Map<Long, Integer> kernings, List<Texture> pages,
            boolean ownsPages) {
        this.name = name != null ? name : "bitmap";
        this.nativeSize = nativeSize > 0.0f ? nativeSize : lineHeight > 0.0f ? lineHeight : 16.0f;
        this.lineHeight = lineHeight > 0.0f ? lineHeight : this.nativeSize;
        this.baseLine = baseLine > 0.0f ? baseLine : this.lineHeight;
        this.glyphs = Collections.unmodifiableMap(new LinkedHashMap<Integer, BitmapFontGlyph>(glyphs));
        this.kernings = Collections.unmodifiableMap(new LinkedHashMap<Long, Integer>(
                kernings != null ? kernings : new LinkedHashMap<Long, Integer>()));
        this.pages = Collections.unmodifiableList(new ArrayList<Texture>(
                pages != null ? pages : new ArrayList<Texture>()));
        this.fallbackGlyph = this.glyphs.get(Integer.valueOf('?'));
        this.asciiGlyphs = new BitmapFontGlyph[ASCII_CACHE_SIZE];
        for (Map.Entry<Integer, BitmapFontGlyph> entry : this.glyphs.entrySet()) {
            int codePoint = entry.getKey().intValue();
            if (codePoint >= 0 && codePoint < ASCII_CACHE_SIZE) {
                asciiGlyphs[codePoint] = entry.getValue();
            }
        }
        this.asciiKernings = new int[ASCII_CACHE_SIZE * ASCII_CACHE_SIZE];
        Arrays.fill(asciiKernings, NO_KERNING);
        for (Map.Entry<Long, Integer> entry : this.kernings.entrySet()) {
            long key = entry.getKey().longValue();
            int first = (int) (key >> 32);
            int second = (int) key;
            if (first >= 0 && first < ASCII_CACHE_SIZE && second >= 0 && second < ASCII_CACHE_SIZE) {
                asciiKernings[first * ASCII_CACHE_SIZE + second] = entry.getValue().intValue();
            }
        }
        this.ownsPages = ownsPages;
    }

    public static BitmapFont fromGrid(Texture texture, String characters, int glyphWidth, int glyphHeight) {
        String text = characters != null ? characters : "";
        Map<Integer, BitmapFontGlyph> glyphs = new LinkedHashMap<Integer, BitmapFontGlyph>();
        int columns = Math.max(1, texture.width() / Math.max(1, glyphWidth));
        Set<Integer> seen = new LinkedHashSet<Integer>();
        for (int i = 0; i < text.length(); i++) {
            int codePoint = text.charAt(i);
            if (!seen.add(Integer.valueOf(codePoint))) {
                continue;
            }
            int column = i % columns;
            int row = i / columns;
            int x = column * glyphWidth;
            int y = row * glyphHeight;
            if (x + glyphWidth <= texture.width() && y + glyphHeight <= texture.height()) {
                glyphs.put(Integer.valueOf(codePoint), new BitmapFontGlyph(codePoint,
                        new TextureRegion(texture, x, y, glyphWidth, glyphHeight), 0.0f, 0.0f, glyphWidth));
            }
        }
        List<Texture> pages = new ArrayList<Texture>();
        pages.add(texture);
        return new BitmapFont("grid", glyphHeight, glyphHeight, glyphHeight, glyphs,
                new LinkedHashMap<Long, Integer>(), pages, false);
    }

    public String name() {
        return name;
    }

    public float nativeSize() {
        return nativeSize;
    }

    public float lineHeight() {
        return lineHeight;
    }

    public float baseLine() {
        return baseLine;
    }

    public BitmapFontGlyph glyph(int codePoint) {
        BitmapFontGlyph glyph = codePoint >= 0 && codePoint < ASCII_CACHE_SIZE
                ? asciiGlyphs[codePoint]
                : null;
        if (glyph != null) {
            return glyph;
        }
        glyph = glyphs.get(Integer.valueOf(codePoint));
        return glyph != null ? glyph : fallbackGlyph;
    }

    public boolean hasGlyph(int codePoint) {
        if (codePoint >= 0 && codePoint < ASCII_CACHE_SIZE && asciiGlyphs[codePoint] != null) {
            return true;
        }
        return glyphs.containsKey(Integer.valueOf(codePoint));
    }

    public int kerning(int first, int second) {
        if (first >= 0 && first < ASCII_CACHE_SIZE && second >= 0 && second < ASCII_CACHE_SIZE) {
            int value = asciiKernings[first * ASCII_CACHE_SIZE + second];
            return value != NO_KERNING ? value : 0;
        }
        Integer value = kernings.get(Long.valueOf(kerningKey(first, second)));
        return value != null ? value.intValue() : 0;
    }

    public float scale(float size) {
        return (size > 0.0f ? size : nativeSize) / nativeSize;
    }

    public float lineHeight(float size) {
        return lineHeight * scale(size);
    }

    public float width(String text, float size) {
        if (text == null || text.length() == 0) {
            return 0.0f;
        }
        float scale = scale(size);
        float width = 0.0f;
        int previous = -1;
        for (int i = 0; i < text.length(); i++) {
            int codePoint = text.charAt(i);
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
        }
        return width;
    }

    public BitmapFontLayout layout(String text, float size, float maxWidth, boolean wrap, boolean ellipsis) {
        List<String> lines = new ArrayList<String>();
        List<Float> widths = new ArrayList<Float>();
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
            widths.add(Float.valueOf(0.0f));
        }
        float max = 0.0f;
        for (int i = 0; i < widths.size(); i++) {
            max = Math.max(max, widths.get(i).floatValue());
        }
        float actualLineHeight = lineHeight(size);
        return new BitmapFontLayout(lines, widths, max, actualLineHeight * lines.size(), actualLineHeight);
    }

    public List<Texture> pages() {
        return pages;
    }

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

    @Override
    public boolean isDisposed() {
        return disposed;
    }

    static long kerningKey(int first, int second) {
        return ((long) first << 32) ^ (second & 0xffffffffL);
    }

    private void appendWrappedLine(String value, float size, float maxWidth, boolean wrap, boolean ellipsis,
            List<String> lines, List<Float> widths) {
        if (!wrap || maxWidth <= 0.0f || width(value, size) <= maxWidth) {
            appendLine(truncate(value, size, maxWidth, ellipsis), size, lines, widths);
            return;
        }
        int start = 0;
        while (start < value.length()) {
            int end = start + 1;
            int best = -1;
            while (end <= value.length()) {
                String candidate = value.substring(start, end);
                float candidateWidth = width(candidate, size);
                if (candidateWidth > maxWidth) {
                    break;
                }
                if (end == value.length() || Character.isWhitespace(value.charAt(end - 1))) {
                    best = end;
                }
                end++;
            }
            if (best <= start) {
                best = Math.max(start + 1, end - 1);
            }
            String line = value.substring(start, best).trim();
            appendLine(line, size, lines, widths);
            start = best;
            while (start < value.length() && Character.isWhitespace(value.charAt(start))) {
                start++;
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
            end--;
        }
        return value.substring(0, end) + marker;
    }

    private void appendLine(String line, float size, List<String> lines, List<Float> widths) {
        lines.add(line);
        widths.add(Float.valueOf(width(line, size)));
    }
}
