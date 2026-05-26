package io.github.libfdx.backend.web;

import io.github.libfdx.runtime.core.FontRasterizer;
import io.github.libfdx.runtime.core.FontRasterizerOptions;
import io.github.libfdx.runtime.core.RasterizedFont;
import io.github.libfdx.runtime.core.RasterizedGlyph;
import io.github.libfdx.runtime.core.RuntimeCoreException;
import java.nio.ByteBuffer;
import java.util.LinkedHashMap;
import java.util.Map;
import org.teavm.jso.JSBody;
import org.teavm.jso.JSObject;
import org.teavm.jso.JSProperty;
import org.teavm.jso.typedarrays.Float32Array;
import org.teavm.jso.typedarrays.Int32Array;
import org.teavm.jso.typedarrays.Int8Array;

final class WebFreeTypeFontRasterizer implements FontRasterizer {
    @Override
    public RasterizedFont rasterize(byte[] fontBytes, FontRasterizerOptions options) {
        if (fontBytes == null || fontBytes.length == 0) {
            throw new RuntimeCoreException("FreeType font data cannot be empty");
        }
        FontRasterizerOptions actualOptions = options != null
                ? options
                : new FontRasterizerOptions(16.0f, null, 2, 512);
        int[] codePoints = codePoints(actualOptions.characters());

        WebFreeTypeResult result = rasterizeNative(fontBytes, codePoints, actualOptions.size(),
                actualOptions.padding(), actualOptions.atlasWidth());
        if (result == null) {
            throw new RuntimeCoreException("FreeType failed to rasterize native web font");
        }

        int atlasWidth = result.getAtlasWidth();
        int atlasHeight = result.getAtlasHeight();
        int glyphCount = result.getGlyphCount();
        int kerningCount = result.getKerningCount();
        if (atlasWidth <= 0 || atlasHeight <= 0 || glyphCount < 0 || kerningCount < 0) {
            throw new RuntimeCoreException("FreeType returned invalid native web font metrics");
        }

        byte[] rgbaBytes = result.getRgba().copyToJavaArray();
        ByteBuffer rgba = ByteBuffer.allocateDirect(rgbaBytes.length);
        rgba.put(rgbaBytes);
        rgba.clear();

        int[] glyphInts = result.getGlyphInts().copyToJavaArray();
        float[] glyphFloats = result.getGlyphFloats().copyToJavaArray();
        int[] kerningInts = result.getKerningInts().copyToJavaArray();

        Map<Integer, RasterizedGlyph> glyphs = new LinkedHashMap<Integer, RasterizedGlyph>();
        for (int i = 0; i < glyphCount; i++) {
            int intIndex = i * 5;
            int floatIndex = i * 3;
            int codePoint = glyphInts[intIndex];
            glyphs.put(Integer.valueOf(codePoint), new RasterizedGlyph(codePoint, glyphInts[intIndex + 1],
                    glyphInts[intIndex + 2], glyphInts[intIndex + 3], glyphInts[intIndex + 4],
                    glyphFloats[floatIndex], glyphFloats[floatIndex + 1], glyphFloats[floatIndex + 2]));
        }

        Map<Long, Integer> kernings = new LinkedHashMap<Long, Integer>();
        for (int i = 0; i < kerningCount; i++) {
            int index = i * 3;
            kernings.put(Long.valueOf(kerningKey(kerningInts[index], kerningInts[index + 1])),
                    Integer.valueOf(kerningInts[index + 2]));
        }

        return new RasterizedFont("freetype", result.getNativeSize(), result.getLineHeight(), result.getBaseLine(),
                atlasWidth, atlasHeight, rgba, glyphs, kernings);
    }

    private int[] codePoints(String characters) {
        String text = characters != null ? characters : "";
        int[] codePoints = new int[text.length()];
        for (int i = 0; i < text.length(); i++) {
            codePoints[i] = text.charAt(i);
        }
        return codePoints;
    }

    private static long kerningKey(int first, int second) {
        return ((long) first << 32) ^ (second & 0xffffffffL);
    }

    @JSBody(params = { "fontBytes", "codePoints", "pixelSize", "padding", "atlasWidth" }, script =
            "var root = typeof window !== 'undefined' ? window : globalThis;\n" +
            "if (!root.libfdxFreeTypeRasterize) {\n" +
            "  throw new Error('libfdx FreeType web runtime was not loaded');\n" +
            "}\n" +
            "return root.libfdxFreeTypeRasterize(fontBytes, codePoints, pixelSize, padding, atlasWidth);")
    private static native WebFreeTypeResult rasterizeNative(byte[] fontBytes, int[] codePoints,
            float pixelSize, int padding, int atlasWidth);

    interface WebFreeTypeResult extends JSObject {
        @JSProperty
        float getNativeSize();

        @JSProperty
        float getLineHeight();

        @JSProperty
        float getBaseLine();

        @JSProperty
        int getAtlasWidth();

        @JSProperty
        int getAtlasHeight();

        @JSProperty
        int getGlyphCount();

        @JSProperty
        int getKerningCount();

        @JSProperty
        Int8Array getRgba();

        @JSProperty
        Int32Array getGlyphInts();

        @JSProperty
        Float32Array getGlyphFloats();

        @JSProperty
        Int32Array getKerningInts();
    }
}
