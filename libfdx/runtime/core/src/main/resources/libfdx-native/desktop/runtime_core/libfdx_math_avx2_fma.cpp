#include <cstdint>

#include <immintrin.h>

namespace {

static void transform_tail_scalar(const float* matrix, float* values, int32_t index) {
    const float x = values[index];
    const float y = values[index + 1];
    const float z = values[index + 2];
    values[index] = matrix[0] * x + matrix[4] * y + matrix[8] * z + matrix[12];
    values[index + 1] = matrix[1] * x + matrix[5] * y + matrix[9] * z + matrix[13];
    values[index + 2] = matrix[2] * x + matrix[6] * y + matrix[10] * z + matrix[14];
}

} // namespace

extern "C" void fdx_math_matrix4_transform_positions_avx2_fma(const float* matrix, float* values, int32_t offset,
        int32_t count, int32_t stride) {
    const __m256 m00 = _mm256_set1_ps(matrix[0]);
    const __m256 m01 = _mm256_set1_ps(matrix[1]);
    const __m256 m02 = _mm256_set1_ps(matrix[2]);
    const __m256 m10 = _mm256_set1_ps(matrix[4]);
    const __m256 m11 = _mm256_set1_ps(matrix[5]);
    const __m256 m12 = _mm256_set1_ps(matrix[6]);
    const __m256 m20 = _mm256_set1_ps(matrix[8]);
    const __m256 m21 = _mm256_set1_ps(matrix[9]);
    const __m256 m22 = _mm256_set1_ps(matrix[10]);
    const __m256 m30 = _mm256_set1_ps(matrix[12]);
    const __m256 m31 = _mm256_set1_ps(matrix[13]);
    const __m256 m32 = _mm256_set1_ps(matrix[14]);

    const __m256i stride_offsets = _mm256_setr_epi32(
            0, stride, stride * 2, stride * 3, stride * 4, stride * 5, stride * 6, stride * 7);
    alignas(32) float out_x[8];
    alignas(32) float out_y[8];
    alignas(32) float out_z[8];

    int32_t processed = 0;
    for (; processed + 8 <= count; processed += 8) {
        const int32_t base = offset + processed * stride;
        const __m256i x_indices = _mm256_add_epi32(_mm256_set1_epi32(base), stride_offsets);
        const __m256i y_indices = _mm256_add_epi32(x_indices, _mm256_set1_epi32(1));
        const __m256i z_indices = _mm256_add_epi32(x_indices, _mm256_set1_epi32(2));
        const __m256 x = _mm256_i32gather_ps(values, x_indices, 4);
        const __m256 y = _mm256_i32gather_ps(values, y_indices, 4);
        const __m256 z = _mm256_i32gather_ps(values, z_indices, 4);

        const __m256 tx = _mm256_fmadd_ps(m20, z, _mm256_fmadd_ps(m10, y, _mm256_fmadd_ps(m00, x, m30)));
        const __m256 ty = _mm256_fmadd_ps(m21, z, _mm256_fmadd_ps(m11, y, _mm256_fmadd_ps(m01, x, m31)));
        const __m256 tz = _mm256_fmadd_ps(m22, z, _mm256_fmadd_ps(m12, y, _mm256_fmadd_ps(m02, x, m32)));

        _mm256_store_ps(out_x, tx);
        _mm256_store_ps(out_y, ty);
        _mm256_store_ps(out_z, tz);
        for (int32_t lane = 0; lane < 8; lane++) {
            const int32_t index = base + lane * stride;
            values[index] = out_x[lane];
            values[index + 1] = out_y[lane];
            values[index + 2] = out_z[lane];
        }
    }

    for (; processed < count; processed++) {
        transform_tail_scalar(matrix, values, offset + processed * stride);
    }
}
