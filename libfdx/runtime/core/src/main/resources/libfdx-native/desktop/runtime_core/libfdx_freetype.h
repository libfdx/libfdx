#ifndef LIBFDX_FREETYPE_H
#define LIBFDX_FREETYPE_H

#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

int32_t fdx_freetype_rasterize(const int8_t* fontData, int32_t fontDataSize, const int32_t* codePoints,
        int32_t codePointCount, float pixelSize, int32_t padding, int32_t atlasWidth, int32_t* metricInts,
        float* metricFloats, void* rgba, int32_t rgbaSize, int32_t* glyphInts, int32_t glyphIntCount,
        float* glyphFloats, int32_t glyphFloatCount, int32_t* kerningInts, int32_t kerningIntCount);

#ifdef __cplusplus
}
#endif

#endif
