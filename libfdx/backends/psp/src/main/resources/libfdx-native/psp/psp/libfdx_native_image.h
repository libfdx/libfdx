#ifndef LIBFDX_NATIVE_IMAGE_H
#define LIBFDX_NATIVE_IMAGE_H

#include <stdint.h>
#include <string.h>

int32_t fdx_native_image_dimensions(const int8_t* data, int32_t size, int32_t* dimensions) {
    int width = 0;
    int height = 0;
    int channels = 0;
    if (data == 0 || size <= 0 || dimensions == 0) {
        return 0;
    }
    if (!stbi_info_from_memory((const unsigned char*) data, size, &width, &height, &channels)) {
        return 0;
    }
    if (width <= 0 || height <= 0) {
        return 0;
    }
    dimensions[0] = (int32_t) width;
    dimensions[1] = (int32_t) height;
    return 1;
}

int32_t fdx_native_image_decode_rgba8(const int8_t* data, int32_t size, void* target, int32_t targetSize) {
    int width = 0;
    int height = 0;
    int channels = 0;
    unsigned char* rgba = 0;
    int32_t requiredSize = 0;
    if (data == 0 || size <= 0 || target == 0 || targetSize <= 0) {
        return 0;
    }
    rgba = stbi_load_from_memory((const unsigned char*) data, size, &width, &height, &channels, 4);
    if (rgba == 0 || width <= 0 || height <= 0) {
        if (rgba != 0) {
            stbi_image_free(rgba);
        }
        return 0;
    }
    requiredSize = (int32_t) (width * height * 4);
    if (requiredSize <= 0 || targetSize < requiredSize) {
        stbi_image_free(rgba);
        return 0;
    }
    memcpy(target, rgba, (size_t) requiredSize);
    stbi_image_free(rgba);
    return 1;
}

#endif
