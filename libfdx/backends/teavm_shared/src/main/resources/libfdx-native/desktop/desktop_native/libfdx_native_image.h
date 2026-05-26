#ifndef LIBFDX_NATIVE_IMAGE_H
#define LIBFDX_NATIVE_IMAGE_H

#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

int32_t fdx_native_image_dimensions(const int8_t* data, int32_t size, int32_t* dimensions);
int32_t fdx_native_image_decode_rgba8(const int8_t* data, int32_t size, void* target, int32_t targetSize);

#ifdef __cplusplus
}
#endif

#endif
