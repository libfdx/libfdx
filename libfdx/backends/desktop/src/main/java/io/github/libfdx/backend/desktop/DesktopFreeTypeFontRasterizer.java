package io.github.libfdx.backend.desktop;

import io.github.libfdx.runtime.core.FontRasterizer;
import io.github.libfdx.runtime.core.FontRasterizerOptions;
import io.github.libfdx.runtime.core.RasterizedFont;
import io.github.libfdx.runtime.core.RasterizedGlyph;
import io.github.libfdx.runtime.core.RuntimeCoreException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.util.freetype.FT_Bitmap;
import org.lwjgl.util.freetype.FT_Face;
import org.lwjgl.util.freetype.FT_GlyphSlot;
import org.lwjgl.util.freetype.FT_Size;
import org.lwjgl.util.freetype.FT_Size_Metrics;
import org.lwjgl.util.freetype.FT_Vector;

import static org.lwjgl.util.freetype.FreeType.FT_Done_Face;
import static org.lwjgl.util.freetype.FreeType.FT_Done_FreeType;
import static org.lwjgl.util.freetype.FreeType.FT_Get_Char_Index;
import static org.lwjgl.util.freetype.FreeType.FT_Get_Kerning;
import static org.lwjgl.util.freetype.FreeType.FT_HAS_KERNING;
import static org.lwjgl.util.freetype.FreeType.FT_Init_FreeType;
import static org.lwjgl.util.freetype.FreeType.FT_KERNING_DEFAULT;
import static org.lwjgl.util.freetype.FreeType.FT_LOAD_DEFAULT;
import static org.lwjgl.util.freetype.FreeType.FT_Load_Char;
import static org.lwjgl.util.freetype.FreeType.FT_New_Memory_Face;
import static org.lwjgl.util.freetype.FreeType.FT_RENDER_MODE_NORMAL;
import static org.lwjgl.util.freetype.FreeType.FT_Render_Glyph;
import static org.lwjgl.util.freetype.FreeType.FT_Set_Pixel_Sizes;

/**
 * Represents a desktop free type font rasterizer.
 *
 * @author xpenatan
 */
final class DesktopFreeTypeFontRasterizer implements FontRasterizer {
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
        ByteBuffer fontData = MemoryUtil.memAlloc(fontBytes.length);
        long library = 0L;
        FT_Face face = null;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            fontData.put(fontBytes);
            fontData.flip();

            PointerBuffer libraryPointer = stack.mallocPointer(1);
            check(FT_Init_FreeType(libraryPointer), "initialize FreeType");
            library = libraryPointer.get(0);

            PointerBuffer facePointer = stack.mallocPointer(1);
            check(FT_New_Memory_Face(library, fontData, 0L, facePointer), "load FreeType memory face");
            face = FT_Face.create(facePointer.get(0));

            int pixelSize = Math.max(1, Math.round(actualOptions.size()));
            check(FT_Set_Pixel_Sizes(face, 0, pixelSize), "set FreeType pixel size");
            return rasterizeFace(face, actualOptions);
        } finally {
            if (face != null) {
                FT_Done_Face(face);
            }
            if (library != 0L) {
                FT_Done_FreeType(library);
            }
            MemoryUtil.memFree(fontData);
        }
    }

    private RasterizedFont rasterizeFace(FT_Face face, FontRasterizerOptions options) {
        FontMetrics metrics = readMetrics(face, options.size());
        List<GlyphBitmap> glyphs = loadGlyphs(face, options, metrics.baseLine);
        int atlasWidth = atlasWidth(options, glyphs);
        int atlasHeight = packGlyphs(glyphs, atlasWidth, options.padding());
        ByteBuffer rgba = ByteBuffer.allocateDirect(atlasWidth * atlasHeight * 4);
        Map<Integer, RasterizedGlyph> rasterizedGlyphs = new LinkedHashMap<Integer, RasterizedGlyph>();
        Map<Integer, Integer> glyphIndices = new LinkedHashMap<Integer, Integer>();

        for (int i = 0; i < glyphs.size(); i++) {
            GlyphBitmap glyph = glyphs.get(i);
            writeGlyph(rgba, atlasWidth, glyph);
            rasterizedGlyphs.put(Integer.valueOf(glyph.codePoint), new RasterizedGlyph(glyph.codePoint, glyph.x,
                    glyph.y, glyph.width, glyph.height, glyph.xOffset, glyph.yOffset, glyph.xAdvance));
            glyphIndices.put(Integer.valueOf(glyph.codePoint), Integer.valueOf(glyph.glyphIndex));
        }
        rgba.clear();

        return new RasterizedFont(fontName(face), options.size(), metrics.lineHeight, metrics.baseLine, atlasWidth,
                atlasHeight, rgba, rasterizedGlyphs, createKernings(face, glyphs, glyphIndices));
    }

    private List<GlyphBitmap> loadGlyphs(FT_Face face, FontRasterizerOptions options, float baseLine) {
        List<GlyphBitmap> glyphs = new ArrayList<GlyphBitmap>();
        String characters = options.characters();
        for (int i = 0; i < characters.length();) {
            int codePoint = characters.codePointAt(i);
            check(FT_Load_Char(face, codePoint, FT_LOAD_DEFAULT), "load glyph " + codePoint);
            check(FT_Render_Glyph(face.glyph(), FT_RENDER_MODE_NORMAL), "render glyph " + codePoint);
            glyphs.add(readGlyph(face, codePoint, baseLine));
            i += Character.charCount(codePoint);
        }
        return glyphs;
    }

    private GlyphBitmap readGlyph(FT_Face face, int codePoint, float baseLine) {
        FT_GlyphSlot slot = face.glyph();
        FT_Bitmap bitmap = slot.bitmap();
        int sourceWidth = bitmap.width();
        int sourceHeight = bitmap.rows();
        int width = Math.max(1, sourceWidth);
        int height = Math.max(1, sourceHeight);
        byte[] alpha = new byte[width * height];
        if (sourceWidth > 0 && sourceHeight > 0) {
            copyAlpha(bitmap, sourceWidth, sourceHeight, width, alpha);
        }
        GlyphBitmap glyph = new GlyphBitmap();
        glyph.codePoint = codePoint;
        glyph.glyphIndex = FT_Get_Char_Index(face, codePoint);
        glyph.width = width;
        glyph.height = height;
        glyph.alpha = alpha;
        glyph.xOffset = slot.bitmap_left();
        glyph.yOffset = baseLine - slot.bitmap_top();
        glyph.xAdvance = pixels(slot.advance().x());
        return glyph;
    }

    private void copyAlpha(FT_Bitmap bitmap, int sourceWidth, int sourceHeight, int targetWidth, byte[] alpha) {
        int pitch = bitmap.pitch();
        int stride = Math.abs(pitch);
        ByteBuffer source = bitmap.buffer(stride * sourceHeight);
        if (source == null) {
            return;
        }
        for (int row = 0; row < sourceHeight; row++) {
            int sourceRow = pitch >= 0 ? row * stride : (sourceHeight - 1 - row) * stride;
            for (int column = 0; column < sourceWidth; column++) {
                alpha[row * targetWidth + column] = source.get(sourceRow + column);
            }
        }
    }

    private FontMetrics readMetrics(FT_Face face, float requestedSize) {
        FT_Size size = face.size();
        if (size == null) {
            return new FontMetrics(requestedSize, requestedSize);
        }
        FT_Size_Metrics metrics = size.metrics();
        float lineHeight = Math.max(1.0f, pixels(metrics.height()));
        float baseLine = Math.max(1.0f, pixels(metrics.ascender()));
        return new FontMetrics(lineHeight, baseLine);
    }

    private int atlasWidth(FontRasterizerOptions options, List<GlyphBitmap> glyphs) {
        int width = options.atlasWidth();
        int padding = options.padding();
        for (int i = 0; i < glyphs.size(); i++) {
            width = Math.max(width, glyphs.get(i).width + padding * 2);
        }
        return width;
    }

    private int packGlyphs(List<GlyphBitmap> glyphs, int atlasWidth, int padding) {
        int x = padding;
        int y = padding;
        int rowHeight = 0;
        int atlasHeight = Math.max(1, padding);
        for (int i = 0; i < glyphs.size(); i++) {
            GlyphBitmap glyph = glyphs.get(i);
            if (x > padding && x + glyph.width + padding > atlasWidth) {
                x = padding;
                y += rowHeight + padding;
                rowHeight = 0;
            }
            glyph.x = x;
            glyph.y = y;
            x += glyph.width + padding;
            rowHeight = Math.max(rowHeight, glyph.height);
            atlasHeight = Math.max(atlasHeight, y + glyph.height + padding);
        }
        return Math.max(1, atlasHeight);
    }

    private void writeGlyph(ByteBuffer rgba, int atlasWidth, GlyphBitmap glyph) {
        for (int row = 0; row < glyph.height; row++) {
            for (int column = 0; column < glyph.width; column++) {
                int alpha = glyph.alpha[row * glyph.width + column] & 0xff;
                int target = ((glyph.y + row) * atlasWidth + glyph.x + column) * 4;
                rgba.put(target, (byte) 255);
                rgba.put(target + 1, (byte) 255);
                rgba.put(target + 2, (byte) 255);
                rgba.put(target + 3, (byte) alpha);
            }
        }
    }

    private Map<Long, Integer> createKernings(FT_Face face, List<GlyphBitmap> glyphs,
            Map<Integer, Integer> glyphIndices) {
        Map<Long, Integer> kernings = new LinkedHashMap<Long, Integer>();
        if (!FT_HAS_KERNING(face)) {
            return kernings;
        }
        try (MemoryStack stack = MemoryStack.stackPush()) {
            FT_Vector kerning = FT_Vector.malloc(stack);
            for (int i = 0; i < glyphs.size(); i++) {
                GlyphBitmap left = glyphs.get(i);
                int leftIndex = glyphIndices.get(Integer.valueOf(left.codePoint)).intValue();
                if (leftIndex == 0) {
                    continue;
                }
                for (int j = 0; j < glyphs.size(); j++) {
                    GlyphBitmap right = glyphs.get(j);
                    int rightIndex = glyphIndices.get(Integer.valueOf(right.codePoint)).intValue();
                    if (rightIndex == 0) {
                        continue;
                    }
                    int error = FT_Get_Kerning(face, leftIndex, rightIndex, FT_KERNING_DEFAULT, kerning);
                    if (error == 0 && kerning.x() != 0L) {
                        kernings.put(Long.valueOf(kerningKey(left.codePoint, right.codePoint)),
                                Integer.valueOf(Math.round(pixels(kerning.x()))));
                    }
                }
            }
        }
        return kernings;
    }

    private String fontName(FT_Face face) {
        String family = face.family_nameString();
        return family != null && family.length() > 0 ? family : "freetype";
    }

    private void check(int error, String action) {
        if (error != 0) {
            throw new RuntimeCoreException("FreeType failed to " + action + " (error " + error + ")");
        }
    }

    private static float pixels(long value) {
        return value / 64.0f;
    }

    private static long kerningKey(int first, int second) {
        return ((long) first << 32) ^ (second & 0xffffffffL);
    }

    /**
     * Represents a font metrics.
     *
     * @author xpenatan
     */
    private static final class FontMetrics {
        final float lineHeight;
        final float baseLine;

        FontMetrics(float lineHeight, float baseLine) {
            this.lineHeight = lineHeight;
            this.baseLine = baseLine;
        }
    }

    /**
     * Represents a glyph bitmap.
     *
     * @author xpenatan
     */
    private static final class GlyphBitmap {
        int codePoint;
        int glyphIndex;
        int x;
        int y;
        int width;
        int height;
        float xOffset;
        float yOffset;
        float xAdvance;
        byte[] alpha;
    }
}
