package io.github.libfdx.backend.web;

import io.github.libfdx.collections.IntMap;
import io.github.libfdx.collections.LongMap;
import io.github.libfdx.runtime.core.FontRasterizer;
import io.github.libfdx.runtime.core.FontRasterizerOptions;
import io.github.libfdx.runtime.core.RasterizedFont;
import io.github.libfdx.runtime.core.RasterizedGlyph;
import io.github.libfdx.runtime.core.RuntimeCoreException;
import java.nio.ByteBuffer;
import org.teavm.jso.JSBody;
import org.teavm.jso.JSObject;
import org.teavm.jso.JSProperty;
import org.teavm.jso.typedarrays.Float32Array;
import org.teavm.jso.typedarrays.Int32Array;
import org.teavm.jso.typedarrays.Int8Array;

/**
 * Represents a web free type font rasterizer.
 *
 * @author xpenatan
 */
final class WebFreeTypeFontRasterizer implements FontRasterizer {
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

        IntMap<RasterizedGlyph> glyphs = new IntMap<RasterizedGlyph>();
        for (int i = 0; i < glyphCount; i++) {
            int intIndex = i * 5;
            int floatIndex = i * 3;
            int codePoint = glyphInts[intIndex];
            glyphs.put(codePoint, new RasterizedGlyph(codePoint, glyphInts[intIndex + 1],
                    glyphInts[intIndex + 2], glyphInts[intIndex + 3], glyphInts[intIndex + 4],
                    glyphFloats[floatIndex], glyphFloats[floatIndex + 1], glyphFloats[floatIndex + 2]));
        }

        LongMap<Integer> kernings = new LongMap<Integer>();
        for (int i = 0; i < kerningCount; i++) {
            int index = i * 3;
            kernings.put(kerningKey(kerningInts[index], kerningInts[index + 1]),
                    Integer.valueOf(kerningInts[index + 2]));
        }

        return new RasterizedFont("freetype", result.getNativeSize(), result.getLineHeight(), result.getBaseLine(),
                atlasWidth, atlasHeight, rgba, glyphs, kernings);
    }

    private int[] codePoints(String characters) {
        String text = characters != null ? characters : "";
        int[] codePoints = new int[text.codePointCount(0, text.length())];
        int index = 0;
        for (int i = 0; i < text.length();) {
            int codePoint = text.codePointAt(i);
            codePoints[index++] = codePoint;
            i += Character.charCount(codePoint);
        }
        return codePoints;
    }

    private static long kerningKey(int first, int second) {
        return ((long) first << 32) ^ (second & 0xffffffffL);
    }

    @JSBody(params = { "fontBytes", "codePoints", "pixelSize", "padding", "atlasWidth" }, script = """
            var root = globalThis;
            function javaArrayLength(source) {
                if (!source) return 0;
                if (typeof source.length === "number") return source.length;
                if (source.data && typeof source.data.length === "number") return source.data.length;
                if (source.$data && typeof source.$data.length === "number") return source.$data.length;
                return 0;
            }

            function copyJavaArray(source, ctor) {
                var length = javaArrayLength(source);
                var target = new ctor(length);
                if (!source || length === 0) return target;
                var data = source.data || source.$data || source;
                if (ArrayBuffer.isView(data)) {
                    target.set(new ctor(data.buffer, data.byteOffset, Math.min(length, data.length)));
                    return target;
                }
                for (var i = 0; i < length; i++) {
                    target[i] = data[i];
                }
                return target;
            }

            var module = root.libfdxCoreModule;
            if (!module) {
                throw new Error("libfdx FreeType Emscripten module is not ready");
            }

            var font = copyJavaArray(fontBytes, Int8Array);
            var points = copyJavaArray(codePoints, Int32Array);
            var fontPtr = 0;
            var codePointPtr = 0;
            var metricIntsPtr = 0;
            var metricFloatsPtr = 0;
            var rgbaPtr = 0;
            var glyphIntsPtr = 0;
            var glyphFloatsPtr = 0;
            var kerningIntsPtr = 0;

            try {
                fontPtr = module._malloc(Math.max(1, font.byteLength));
                codePointPtr = module._malloc(Math.max(1, points.byteLength));
                metricIntsPtr = module._malloc(16);
                metricFloatsPtr = module._malloc(12);
                module.HEAP8.set(font, fontPtr);
                module.HEAP32.set(points, codePointPtr >> 2);

                var measured = module._fdx_freetype_rasterize(fontPtr, font.byteLength, codePointPtr, points.length,
                        pixelSize, padding, atlasWidth, metricIntsPtr, metricFloatsPtr, 0, 0, 0, 0, 0, 0, 0, 0);
                if (!measured) {
                    throw new Error("FreeType failed to measure native web font");
                }

                var metricInts = new Int32Array(module.HEAP32.buffer, metricIntsPtr, 4);
                var metricFloats = new Float32Array(module.HEAPF32.buffer, metricFloatsPtr, 3);
                var width = metricInts[0];
                var height = metricInts[1];
                var glyphCount = metricInts[2];
                var kerningCount = metricInts[3];
                if (width <= 0 || height <= 0 || glyphCount < 0 || kerningCount < 0) {
                    throw new Error("FreeType returned invalid native web font metrics");
                }

                var rgbaSize = width * height * 4;
                rgbaPtr = module._malloc(Math.max(1, rgbaSize));
                glyphIntsPtr = module._malloc(Math.max(1, glyphCount * 5 * 4));
                glyphFloatsPtr = module._malloc(Math.max(1, glyphCount * 3 * 4));
                kerningIntsPtr = module._malloc(Math.max(1, kerningCount * 3 * 4));

                var rasterized = module._fdx_freetype_rasterize(fontPtr, font.byteLength, codePointPtr, points.length,
                        pixelSize, padding, atlasWidth, metricIntsPtr, metricFloatsPtr, rgbaPtr, rgbaSize,
                        glyphIntsPtr, glyphCount * 5, glyphFloatsPtr, glyphCount * 3, kerningIntsPtr, kerningCount * 3);
                if (!rasterized) {
                    throw new Error("FreeType failed to rasterize native web font");
                }

                return {
                    nativeSize: metricFloats[0],
                    lineHeight: metricFloats[1],
                    baseLine: metricFloats[2],
                    atlasWidth: width,
                    atlasHeight: height,
                    glyphCount: glyphCount,
                    kerningCount: kerningCount,
                    rgba: new Int8Array(module.HEAPU8.slice(rgbaPtr, rgbaPtr + rgbaSize).buffer),
                    glyphInts: new Int32Array(module.HEAP32.slice(glyphIntsPtr >> 2, (glyphIntsPtr >> 2) + glyphCount * 5).buffer),
                    glyphFloats: new Float32Array(module.HEAPF32.slice(glyphFloatsPtr >> 2, (glyphFloatsPtr >> 2) + glyphCount * 3).buffer),
                    kerningInts: new Int32Array(module.HEAP32.slice(kerningIntsPtr >> 2, (kerningIntsPtr >> 2) + kerningCount * 3).buffer)
                };
            } finally {
                if (kerningIntsPtr) module._free(kerningIntsPtr);
                if (glyphFloatsPtr) module._free(glyphFloatsPtr);
                if (glyphIntsPtr) module._free(glyphIntsPtr);
                if (rgbaPtr) module._free(rgbaPtr);
                if (metricFloatsPtr) module._free(metricFloatsPtr);
                if (metricIntsPtr) module._free(metricIntsPtr);
                if (codePointPtr) module._free(codePointPtr);
                if (fontPtr) module._free(fontPtr);
            }
        """)
    private static native WebFreeTypeResult rasterizeNative(byte[] fontBytes, int[] codePoints,
            float pixelSize, int padding, int atlasWidth);

    /**
     * Defines the contract for web free type result implementations.
     *
     * @author xpenatan
     */
    interface WebFreeTypeResult extends JSObject {
        /**
         * Returns the native size.
         *
         * @return the get native size
         */
        @JSProperty
        float getNativeSize();

        /**
         * Returns the line height.
         *
         * @return the get line height
         */
        @JSProperty
        float getLineHeight();

        /**
         * Returns the base line.
         *
         * @return the get base line
         */
        @JSProperty
        float getBaseLine();

        /**
         * Returns the atlas width.
         *
         * @return the get atlas width
         */
        @JSProperty
        int getAtlasWidth();

        /**
         * Returns the atlas height.
         *
         * @return the get atlas height
         */
        @JSProperty
        int getAtlasHeight();

        /**
         * Returns the glyph count.
         *
         * @return the get glyph count
         */
        @JSProperty
        int getGlyphCount();

        /**
         * Returns the kerning count.
         *
         * @return the get kerning count
         */
        @JSProperty
        int getKerningCount();

        /**
         * Returns the RGBA.
         *
         * @return the get RGBA
         */
        @JSProperty
        Int8Array getRgba();

        /**
         * Returns the glyph ints.
         *
         * @return the get glyph ints
         */
        @JSProperty
        Int32Array getGlyphInts();

        /**
         * Returns the glyph floats.
         *
         * @return the get glyph floats
         */
        @JSProperty
        Float32Array getGlyphFloats();

        /**
         * Returns the kerning ints.
         *
         * @return the get kerning ints
         */
        @JSProperty
        Int32Array getKerningInts();
    }
}
