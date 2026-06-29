#ifndef LIBFDX_MATH_H
#define LIBFDX_MATH_H

#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

int32_t fdx_math_simd_available(void);
const char* fdx_math_acceleration_name(void);
int32_t fdx_math_matrix4_mul(const float* left, const float* right, float* out);
int32_t fdx_math_matrix4_transform_positions(const float* matrix, float* values, int32_t offset,
        int32_t count, int32_t stride);

#ifdef __cplusplus
}
#endif

#endif
