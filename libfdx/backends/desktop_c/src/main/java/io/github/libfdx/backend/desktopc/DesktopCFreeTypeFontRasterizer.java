package io.github.libfdx.backend.desktopc;

import io.github.libfdx.runtime.core.FontRasterizer;
import io.github.libfdx.runtime.core.FontRasterizerOptions;
import io.github.libfdx.runtime.core.RasterizedFont;
import io.github.libfdx.runtime.core.RasterizedGlyph;
import io.github.libfdx.runtime.core.RuntimeCoreException;
import java.nio.ByteBuffer;
import java.util.LinkedHashMap;
import java.util.Map;
import org.teavm.interop.Address;
import org.teavm.interop.Import;
import org.teavm.interop.c.Include;

/**
 * Represents a desktop C free type font rasterizer.
 *
 * @author xpenatan
 */
@Include("libfdx_freetype.h")
final class DesktopCFreeTypeFontRasterizer implements FontRasterizer {
    /**
     * Runs the rasterize step.
     *
     * @param fontBytes the font bytes
     * @param options the options
     * @return the rasterize
     */
    @Override
    public RasterizedFont rasterize(byte[] fontBytes, FontRasterizerOptions options) {
        if (fontBytes == null || fontBytes.length == 0) {
            throw new RuntimeCoreException("FreeType font data cannot be empty");
        }
        FontRasterizerOptions actualOptions = options != null
                ? options
                : new FontRasterizerOptions(16.0f, null, 2, 512);
        int[] codePoints = codePoints(actualOptions.characters());
        int[] metricInts = new int[4];
        float[] metricFloats = new float[3];
        ByteBuffer emptyTarget = ByteBuffer.allocateDirect(1);

        int measured = fdxFreeTypeRasterize(fontBytes, fontBytes.length, Address.ofData(codePoints),
                codePoints.length, actualOptions.size(), actualOptions.padding(), actualOptions.atlasWidth(),
                Address.ofData(metricInts), Address.ofData(metricFloats), emptyTarget, 0, Address.fromLong(0L), 0,
                Address.fromLong(0L), 0, Address.fromLong(0L), 0);
        if (measured == 0) {
            throw new RuntimeCoreException("FreeType failed to measure native font");
        }

        int atlasWidth = metricInts[0];
        int atlasHeight = metricInts[1];
        int glyphCount = metricInts[2];
        int kerningCount = metricInts[3];
        if (atlasWidth <= 0 || atlasHeight <= 0 || glyphCount < 0 || kerningCount < 0) {
            throw new RuntimeCoreException("FreeType returned invalid native font metrics");
        }

        ByteBuffer rgba = ByteBuffer.allocateDirect(atlasWidth * atlasHeight * 4);
        int[] glyphInts = new int[glyphCount * 5];
        float[] glyphFloats = new float[glyphCount * 3];
        int[] kerningInts = new int[kerningCount * 3];
        int rasterized = fdxFreeTypeRasterize(fontBytes, fontBytes.length, Address.ofData(codePoints),
                codePoints.length, actualOptions.size(), actualOptions.padding(), actualOptions.atlasWidth(),
                Address.ofData(metricInts), Address.ofData(metricFloats), rgba, rgba.capacity(),
                Address.ofData(glyphInts), glyphInts.length, Address.ofData(glyphFloats), glyphFloats.length,
                Address.ofData(kerningInts), kerningInts.length);
        if (rasterized == 0) {
            throw new RuntimeCoreException("FreeType failed to rasterize native font");
        }
        rgba.clear();

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

        return new RasterizedFont("freetype", metricFloats[0], metricFloats[1], metricFloats[2],
                metricInts[0], metricInts[1], rgba, glyphs, kernings);
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

    @Import(name = "fdx_freetype_rasterize")
    private static native int fdxFreeTypeRasterize(byte[] fontData, int fontDataSize, Address codePoints,
            int codePointCount, float pixelSize, int padding, int atlasWidth, Address metricInts,
            Address metricFloats, ByteBuffer rgba, int rgbaSize, Address glyphInts, int glyphIntCount,
            Address glyphFloats, int glyphFloatCount, Address kerningInts, int kerningIntCount);
}
