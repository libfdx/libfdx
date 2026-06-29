#include "libfdx_freetype.h"

#include <algorithm>
#include <cmath>
#include <cstdint>
#include <cstdio>
#include <cstring>
#include <vector>

#include <ft2build.h>
#include FT_FREETYPE_H

#define FDX_FREETYPE_LOGE(...) std::fprintf(stderr, "[libfdx-freetype] error: " __VA_ARGS__), std::fprintf(stderr, "\n")

namespace {

struct GlyphBitmap {
    int32_t codePoint = 0;
    FT_UInt glyphIndex = 0;
    int32_t x = 0;
    int32_t y = 0;
    int32_t width = 1;
    int32_t height = 1;
    float xOffset = 0.0f;
    float yOffset = 0.0f;
    float xAdvance = 0.0f;
    std::vector<uint8_t> alpha;
};

struct Kerning {
    int32_t first = 0;
    int32_t second = 0;
    int32_t amount = 0;
};

struct RasterizedFace {
    float nativeSize = 16.0f;
    float lineHeight = 16.0f;
    float baseLine = 16.0f;
    int32_t atlasWidth = 64;
    int32_t atlasHeight = 1;
    std::vector<GlyphBitmap> glyphs;
    std::vector<Kerning> kernings;
};

static float pixels(FT_Pos value) {
    return static_cast<float>(value) / 64.0f;
}

static void copyAlpha(const FT_Bitmap& bitmap, GlyphBitmap& glyph) {
    int32_t sourceWidth = static_cast<int32_t>(bitmap.width);
    int32_t sourceHeight = static_cast<int32_t>(bitmap.rows);
    glyph.width = std::max<int32_t>(1, sourceWidth);
    glyph.height = std::max<int32_t>(1, sourceHeight);
    glyph.alpha.assign(static_cast<size_t>(glyph.width) * static_cast<size_t>(glyph.height), 0u);
    if (sourceWidth <= 0 || sourceHeight <= 0 || bitmap.buffer == nullptr) {
        return;
    }

    int32_t pitch = bitmap.pitch;
    int32_t stride = std::abs(pitch);
    for (int32_t row = 0; row < sourceHeight; row++) {
        int32_t sourceRow = pitch >= 0 ? row * stride : (sourceHeight - 1 - row) * stride;
        for (int32_t column = 0; column < sourceWidth; column++) {
            glyph.alpha[static_cast<size_t>(row) * glyph.width + column] =
                    bitmap.buffer[sourceRow + column];
        }
    }
}

static bool loadGlyphs(FT_Face face, const int32_t* codePoints, int32_t codePointCount, RasterizedFace& output) {
    output.glyphs.clear();
    output.glyphs.reserve(static_cast<size_t>(std::max<int32_t>(0, codePointCount)));
    for (int32_t i = 0; i < codePointCount; i++) {
        int32_t codePoint = codePoints[i];
        FT_Error error = FT_Load_Char(face, static_cast<FT_ULong>(codePoint), FT_LOAD_DEFAULT);
        if (error != 0) {
            FDX_FREETYPE_LOGE("Could not load glyph %d: %d", codePoint, static_cast<int>(error));
            return false;
        }
        error = FT_Render_Glyph(face->glyph, FT_RENDER_MODE_NORMAL);
        if (error != 0) {
            FDX_FREETYPE_LOGE("Could not render glyph %d: %d", codePoint, static_cast<int>(error));
            return false;
        }

        FT_GlyphSlot slot = face->glyph;
        GlyphBitmap glyph;
        glyph.codePoint = codePoint;
        glyph.glyphIndex = FT_Get_Char_Index(face, static_cast<FT_ULong>(codePoint));
        copyAlpha(slot->bitmap, glyph);
        glyph.xOffset = static_cast<float>(slot->bitmap_left);
        glyph.yOffset = output.baseLine - static_cast<float>(slot->bitmap_top);
        glyph.xAdvance = pixels(slot->advance.x);
        output.glyphs.push_back(glyph);
    }
    return true;
}

static int32_t atlasWidth(int32_t requestedAtlasWidth, int32_t padding, const std::vector<GlyphBitmap>& glyphs) {
    int32_t width = std::max<int32_t>(64, requestedAtlasWidth);
    for (const GlyphBitmap& glyph : glyphs) {
        width = std::max<int32_t>(width, glyph.width + padding * 2);
    }
    return width;
}

static void packGlyphs(RasterizedFace& output, int32_t padding) {
    int32_t x = padding;
    int32_t y = padding;
    int32_t rowHeight = 0;
    output.atlasHeight = std::max<int32_t>(1, padding);
    for (GlyphBitmap& glyph : output.glyphs) {
        if (x > padding && x + glyph.width + padding > output.atlasWidth) {
            x = padding;
            y += rowHeight + padding;
            rowHeight = 0;
        }
        glyph.x = x;
        glyph.y = y;
        x += glyph.width + padding;
        rowHeight = std::max<int32_t>(rowHeight, glyph.height);
        output.atlasHeight = std::max<int32_t>(output.atlasHeight, y + glyph.height + padding);
    }
}

static void createKernings(FT_Face face, RasterizedFace& output) {
    output.kernings.clear();
    if (!FT_HAS_KERNING(face)) {
        return;
    }
    FT_Vector kerning;
    for (const GlyphBitmap& left : output.glyphs) {
        if (left.glyphIndex == 0) {
            continue;
        }
        for (const GlyphBitmap& right : output.glyphs) {
            if (right.glyphIndex == 0) {
                continue;
            }
            FT_Error error = FT_Get_Kerning(face, left.glyphIndex, right.glyphIndex, FT_KERNING_DEFAULT, &kerning);
            if (error == 0 && kerning.x != 0) {
                Kerning value;
                value.first = left.codePoint;
                value.second = right.codePoint;
                value.amount = static_cast<int32_t>(std::lround(pixels(kerning.x)));
                if (value.amount != 0) {
                    output.kernings.push_back(value);
                }
            }
        }
    }
}

static bool rasterize(const int8_t* fontData, int32_t fontDataSize, const int32_t* codePoints,
        int32_t codePointCount, float pixelSize, int32_t padding, int32_t requestedAtlasWidth,
        RasterizedFace& output) {
    if (fontData == nullptr || fontDataSize <= 0 || codePoints == nullptr || codePointCount < 0) {
        return false;
    }

    FT_Library library = nullptr;
    FT_Error error = FT_Init_FreeType(&library);
    if (error != 0) {
        FDX_FREETYPE_LOGE("Could not initialize FreeType: %d", static_cast<int>(error));
        return false;
    }

    FT_Face face = nullptr;
    bool success = false;
    do {
        error = FT_New_Memory_Face(library, reinterpret_cast<const FT_Byte*>(fontData),
                static_cast<FT_Long>(fontDataSize), 0, &face);
        if (error != 0) {
            FDX_FREETYPE_LOGE("Could not load memory face: %d", static_cast<int>(error));
            break;
        }
        int32_t actualPixelSize = std::max<int32_t>(1, static_cast<int32_t>(std::lround(pixelSize)));
        error = FT_Set_Pixel_Sizes(face, 0, static_cast<FT_UInt>(actualPixelSize));
        if (error != 0) {
            FDX_FREETYPE_LOGE("Could not set pixel size: %d", static_cast<int>(error));
            break;
        }

        output.nativeSize = pixelSize > 0.0f ? pixelSize : static_cast<float>(actualPixelSize);
        output.lineHeight = face->size != nullptr ? std::max(1.0f, pixels(face->size->metrics.height)) : output.nativeSize;
        output.baseLine = face->size != nullptr ? std::max(1.0f, pixels(face->size->metrics.ascender)) : output.nativeSize;
        if (!loadGlyphs(face, codePoints, codePointCount, output)) {
            break;
        }
        output.atlasWidth = atlasWidth(requestedAtlasWidth, std::max<int32_t>(0, padding), output.glyphs);
        packGlyphs(output, std::max<int32_t>(0, padding));
        createKernings(face, output);
        success = true;
    } while (false);

    if (face != nullptr) {
        FT_Done_Face(face);
    }
    FT_Done_FreeType(library);
    return success;
}

static void writeRgba(uint8_t* target, int32_t targetSize, const RasterizedFace& font) {
    std::memset(target, 0, static_cast<size_t>(targetSize));
    for (const GlyphBitmap& glyph : font.glyphs) {
        for (int32_t row = 0; row < glyph.height; row++) {
            for (int32_t column = 0; column < glyph.width; column++) {
                uint8_t alpha = glyph.alpha[static_cast<size_t>(row) * glyph.width + column];
                size_t index = (static_cast<size_t>(glyph.y + row) * font.atlasWidth + glyph.x + column) * 4u;
                target[index] = 255u;
                target[index + 1u] = 255u;
                target[index + 2u] = 255u;
                target[index + 3u] = alpha;
            }
        }
    }
}

} // namespace

extern "C" int32_t fdx_freetype_rasterize(const int8_t* fontData, int32_t fontDataSize,
        const int32_t* codePoints, int32_t codePointCount, float pixelSize, int32_t padding,
        int32_t atlasWidth, int32_t* metricInts, float* metricFloats, void* rgba, int32_t rgbaSize,
        int32_t* glyphInts, int32_t glyphIntCount, float* glyphFloats, int32_t glyphFloatCount,
        int32_t* kerningInts, int32_t kerningIntCount) {
    RasterizedFace font;
    if (!rasterize(fontData, fontDataSize, codePoints, codePointCount, pixelSize, padding, atlasWidth, font)) {
        return 0;
    }

    if (metricInts != nullptr) {
        metricInts[0] = font.atlasWidth;
        metricInts[1] = font.atlasHeight;
        metricInts[2] = static_cast<int32_t>(font.glyphs.size());
        metricInts[3] = static_cast<int32_t>(font.kernings.size());
    }
    if (metricFloats != nullptr) {
        metricFloats[0] = font.nativeSize;
        metricFloats[1] = font.lineHeight;
        metricFloats[2] = font.baseLine;
    }

    if (rgba == nullptr || rgbaSize == 0) {
        return 1;
    }

    int64_t requiredRgbaSize = static_cast<int64_t>(font.atlasWidth) * static_cast<int64_t>(font.atlasHeight) * 4ll;
    if (requiredRgbaSize <= 0 || requiredRgbaSize > rgbaSize) {
        FDX_FREETYPE_LOGE("RGBA target buffer is too small");
        return 0;
    }
    if (glyphIntCount < static_cast<int32_t>(font.glyphs.size() * 5u)
            || glyphFloatCount < static_cast<int32_t>(font.glyphs.size() * 3u)
            || kerningIntCount < static_cast<int32_t>(font.kernings.size() * 3u)) {
        FDX_FREETYPE_LOGE("FreeType output arrays are too small");
        return 0;
    }

    writeRgba(static_cast<uint8_t*>(rgba), static_cast<int32_t>(requiredRgbaSize), font);
    if (glyphInts != nullptr && glyphFloats != nullptr) {
        for (size_t i = 0; i < font.glyphs.size(); i++) {
            const GlyphBitmap& glyph = font.glyphs[i];
            size_t intIndex = i * 5u;
            size_t floatIndex = i * 3u;
            glyphInts[intIndex] = glyph.codePoint;
            glyphInts[intIndex + 1u] = glyph.x;
            glyphInts[intIndex + 2u] = glyph.y;
            glyphInts[intIndex + 3u] = glyph.width;
            glyphInts[intIndex + 4u] = glyph.height;
            glyphFloats[floatIndex] = glyph.xOffset;
            glyphFloats[floatIndex + 1u] = glyph.yOffset;
            glyphFloats[floatIndex + 2u] = glyph.xAdvance;
        }
    }
    if (kerningInts != nullptr) {
        for (size_t i = 0; i < font.kernings.size(); i++) {
            const Kerning& kerning = font.kernings[i];
            size_t index = i * 3u;
            kerningInts[index] = kerning.first;
            kerningInts[index + 1u] = kerning.second;
            kerningInts[index + 2u] = kerning.amount;
        }
    }
    return 1;
}
