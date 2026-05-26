#include "libfdx_math.h"

#include <cstdint>
#include <cstring>

#if defined(_M_X64) || defined(_M_IX86) || defined(__x86_64__) || defined(__i386__)
#define LIBFDX_MATH_X86 1
#if defined(_MSC_VER)
#include <intrin.h>
#else
#include <cpuid.h>
#endif
#else
#define LIBFDX_MATH_X86 0
#endif

#if defined(__SSE__) || defined(_M_X64) || (defined(_M_IX86_FP) && _M_IX86_FP >= 1)
#define LIBFDX_MATH_SSE 1
#include <xmmintrin.h>
#else
#define LIBFDX_MATH_SSE 0
#endif

#if defined(__ARM_NEON) || defined(__ARM_NEON__) || defined(_M_ARM64)
#define LIBFDX_MATH_NEON 1
#include <arm_neon.h>
#else
#define LIBFDX_MATH_NEON 0
#endif

namespace {

struct MathDispatch {
    bool avx2_fma;
    bool sse;
    bool neon;
    const char* name;
};

static void matrix4_mul_scalar(const float* left, const float* right, float* out) {
    float tmp[16];
    tmp[0] = left[0] * right[0] + left[4] * right[1] + left[8] * right[2] + left[12] * right[3];
    tmp[1] = left[1] * right[0] + left[5] * right[1] + left[9] * right[2] + left[13] * right[3];
    tmp[2] = left[2] * right[0] + left[6] * right[1] + left[10] * right[2] + left[14] * right[3];
    tmp[3] = left[3] * right[0] + left[7] * right[1] + left[11] * right[2] + left[15] * right[3];
    tmp[4] = left[0] * right[4] + left[4] * right[5] + left[8] * right[6] + left[12] * right[7];
    tmp[5] = left[1] * right[4] + left[5] * right[5] + left[9] * right[6] + left[13] * right[7];
    tmp[6] = left[2] * right[4] + left[6] * right[5] + left[10] * right[6] + left[14] * right[7];
    tmp[7] = left[3] * right[4] + left[7] * right[5] + left[11] * right[6] + left[15] * right[7];
    tmp[8] = left[0] * right[8] + left[4] * right[9] + left[8] * right[10] + left[12] * right[11];
    tmp[9] = left[1] * right[8] + left[5] * right[9] + left[9] * right[10] + left[13] * right[11];
    tmp[10] = left[2] * right[8] + left[6] * right[9] + left[10] * right[10] + left[14] * right[11];
    tmp[11] = left[3] * right[8] + left[7] * right[9] + left[11] * right[10] + left[15] * right[11];
    tmp[12] = left[0] * right[12] + left[4] * right[13] + left[8] * right[14] + left[12] * right[15];
    tmp[13] = left[1] * right[12] + left[5] * right[13] + left[9] * right[14] + left[13] * right[15];
    tmp[14] = left[2] * right[12] + left[6] * right[13] + left[10] * right[14] + left[14] * right[15];
    tmp[15] = left[3] * right[12] + left[7] * right[13] + left[11] * right[14] + left[15] * right[15];
    std::memcpy(out, tmp, sizeof(tmp));
}

static void matrix4_transform_positions_scalar(const float* matrix, float* values, int32_t offset,
        int32_t count, int32_t stride) {
    int32_t index = offset;
    for (int32_t i = 0; i < count; i++) {
        float x = values[index];
        float y = values[index + 1];
        float z = values[index + 2];
        values[index] = matrix[0] * x + matrix[4] * y + matrix[8] * z + matrix[12];
        values[index + 1] = matrix[1] * x + matrix[5] * y + matrix[9] * z + matrix[13];
        values[index + 2] = matrix[2] * x + matrix[6] * y + matrix[10] * z + matrix[14];
        index += stride;
    }
}

#if LIBFDX_MATH_X86
static bool cpuid(uint32_t leaf, uint32_t subleaf, uint32_t* eax, uint32_t* ebx, uint32_t* ecx, uint32_t* edx) {
#if defined(_MSC_VER)
    int registers[4];
    __cpuidex(registers, static_cast<int>(leaf), static_cast<int>(subleaf));
    *eax = static_cast<uint32_t>(registers[0]);
    *ebx = static_cast<uint32_t>(registers[1]);
    *ecx = static_cast<uint32_t>(registers[2]);
    *edx = static_cast<uint32_t>(registers[3]);
    return true;
#else
    return __get_cpuid_count(leaf, subleaf, eax, ebx, ecx, edx) != 0;
#endif
}

static uint64_t xgetbv0(void) {
#if defined(_MSC_VER)
    return _xgetbv(0);
#else
    uint32_t eax;
    uint32_t edx;
    __asm__ volatile("xgetbv" : "=a"(eax), "=d"(edx) : "c"(0));
    return (static_cast<uint64_t>(edx) << 32) | eax;
#endif
}

static bool cpu_supports_avx2_fma(void) {
    uint32_t eax = 0;
    uint32_t ebx = 0;
    uint32_t ecx = 0;
    uint32_t edx = 0;
    if (!cpuid(1, 0, &eax, &ebx, &ecx, &edx)) {
        return false;
    }
    const bool osxsave = (ecx & (1u << 27)) != 0;
    const bool avx = (ecx & (1u << 28)) != 0;
    const bool fma = (ecx & (1u << 12)) != 0;
    if (!osxsave || !avx || !fma) {
        return false;
    }
    if ((xgetbv0() & 0x6u) != 0x6u) {
        return false;
    }
    if (!cpuid(7, 0, &eax, &ebx, &ecx, &edx)) {
        return false;
    }
    return (ebx & (1u << 5)) != 0;
}
#endif

#if LIBFDX_MATH_SSE
static void matrix4_mul_sse(const float* left, const float* right, float* out) {
    const __m128 c0 = _mm_loadu_ps(left);
    const __m128 c1 = _mm_loadu_ps(left + 4);
    const __m128 c2 = _mm_loadu_ps(left + 8);
    const __m128 c3 = _mm_loadu_ps(left + 12);
    float tmp[16];
    for (int32_t column = 0; column < 16; column += 4) {
        __m128 value = _mm_mul_ps(c0, _mm_set1_ps(right[column]));
        value = _mm_add_ps(value, _mm_mul_ps(c1, _mm_set1_ps(right[column + 1])));
        value = _mm_add_ps(value, _mm_mul_ps(c2, _mm_set1_ps(right[column + 2])));
        value = _mm_add_ps(value, _mm_mul_ps(c3, _mm_set1_ps(right[column + 3])));
        _mm_storeu_ps(tmp + column, value);
    }
    std::memcpy(out, tmp, sizeof(tmp));
}

static void matrix4_transform_positions_sse(const float* matrix, float* values, int32_t offset,
        int32_t count, int32_t stride) {
    const __m128 c0 = _mm_loadu_ps(matrix);
    const __m128 c1 = _mm_loadu_ps(matrix + 4);
    const __m128 c2 = _mm_loadu_ps(matrix + 8);
    const __m128 c3 = _mm_loadu_ps(matrix + 12);
    int32_t index = offset;
    for (int32_t i = 0; i < count; i++) {
        __m128 result = _mm_mul_ps(c0, _mm_set1_ps(values[index]));
        result = _mm_add_ps(result, _mm_mul_ps(c1, _mm_set1_ps(values[index + 1])));
        result = _mm_add_ps(result, _mm_mul_ps(c2, _mm_set1_ps(values[index + 2])));
        result = _mm_add_ps(result, c3);
        float tmp[4];
        _mm_storeu_ps(tmp, result);
        values[index] = tmp[0];
        values[index + 1] = tmp[1];
        values[index + 2] = tmp[2];
        index += stride;
    }
}
#endif

#if LIBFDX_MATH_NEON
static void matrix4_mul_neon(const float* left, const float* right, float* out) {
    const float32x4_t c0 = vld1q_f32(left);
    const float32x4_t c1 = vld1q_f32(left + 4);
    const float32x4_t c2 = vld1q_f32(left + 8);
    const float32x4_t c3 = vld1q_f32(left + 12);
    float tmp[16];
    for (int32_t column = 0; column < 16; column += 4) {
        float32x4_t value = vmulq_n_f32(c0, right[column]);
        value = vmlaq_n_f32(value, c1, right[column + 1]);
        value = vmlaq_n_f32(value, c2, right[column + 2]);
        value = vmlaq_n_f32(value, c3, right[column + 3]);
        vst1q_f32(tmp + column, value);
    }
    std::memcpy(out, tmp, sizeof(tmp));
}

static void matrix4_transform_positions_neon(const float* matrix, float* values, int32_t offset,
        int32_t count, int32_t stride) {
    const float32x4_t c0 = vld1q_f32(matrix);
    const float32x4_t c1 = vld1q_f32(matrix + 4);
    const float32x4_t c2 = vld1q_f32(matrix + 8);
    const float32x4_t c3 = vld1q_f32(matrix + 12);
    int32_t index = offset;
    for (int32_t i = 0; i < count; i++) {
        float32x4_t result = vmulq_n_f32(c0, values[index]);
        result = vmlaq_n_f32(result, c1, values[index + 1]);
        result = vmlaq_n_f32(result, c2, values[index + 2]);
        result = vaddq_f32(result, c3);
        float tmp[4];
        vst1q_f32(tmp, result);
        values[index] = tmp[0];
        values[index + 1] = tmp[1];
        values[index + 2] = tmp[2];
        index += stride;
    }
}
#endif

#if defined(LIBFDX_MATH_HAS_AVX2_FMA)
extern "C" void fdx_math_matrix4_transform_positions_avx2_fma(const float* matrix, float* values, int32_t offset,
        int32_t count, int32_t stride);
#endif

static MathDispatch detect_dispatch(void) {
    MathDispatch result = {};
#if defined(LIBFDX_MATH_HAS_AVX2_FMA) && LIBFDX_MATH_X86
    if (cpu_supports_avx2_fma()) {
        result.avx2_fma = true;
        result.sse = LIBFDX_MATH_SSE != 0;
        result.name = "avx2-fma";
        return result;
    }
#endif
#if LIBFDX_MATH_SSE
    result.sse = true;
    result.name = "sse";
    return result;
#elif LIBFDX_MATH_NEON
    result.neon = true;
    result.name = "neon";
    return result;
#else
    result.name = "scalar";
    return result;
#endif
}

static const MathDispatch& dispatch(void) {
    static const MathDispatch value = detect_dispatch();
    return value;
}

} // namespace

extern "C" int32_t fdx_math_simd_available(void) {
    const MathDispatch& kernel = dispatch();
    return kernel.avx2_fma || kernel.sse || kernel.neon ? 1 : 0;
}

extern "C" const char* fdx_math_acceleration_name(void) {
    return dispatch().name;
}

extern "C" int32_t fdx_math_matrix4_mul(const float* left, const float* right, float* out) {
    if (left == nullptr || right == nullptr || out == nullptr) {
        return 0;
    }
    const MathDispatch& kernel = dispatch();
#if LIBFDX_MATH_SSE
    if (kernel.sse) {
        matrix4_mul_sse(left, right, out);
        return 1;
    }
#endif
#if LIBFDX_MATH_NEON
    if (kernel.neon) {
        matrix4_mul_neon(left, right, out);
        return 1;
    }
#endif
    matrix4_mul_scalar(left, right, out);
    return 1;
}

extern "C" int32_t fdx_math_matrix4_transform_positions(const float* matrix, float* values, int32_t offset,
        int32_t count, int32_t stride) {
    if (matrix == nullptr || values == nullptr || offset < 0 || count < 0 || stride < 3) {
        return 0;
    }
    if (count == 0) {
        return 1;
    }
    const MathDispatch& kernel = dispatch();
#if defined(LIBFDX_MATH_HAS_AVX2_FMA)
    if (kernel.avx2_fma) {
        fdx_math_matrix4_transform_positions_avx2_fma(matrix, values, offset, count, stride);
        return 1;
    }
#endif
#if LIBFDX_MATH_SSE
    if (kernel.sse) {
        matrix4_transform_positions_sse(matrix, values, offset, count, stride);
        return 1;
    }
#endif
#if LIBFDX_MATH_NEON
    if (kernel.neon) {
        matrix4_transform_positions_neon(matrix, values, offset, count, stride);
        return 1;
    }
#endif
    matrix4_transform_positions_scalar(matrix, values, offset, count, stride);
    return 1;
}
