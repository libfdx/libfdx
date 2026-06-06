#include <jni.h>
#include <algorithm>
#include <cstdint>

#include "libfdx_freetype.h"

extern "C" JNIEXPORT jint JNICALL Java_io_github_libfdx_backend_android_AndroidFreeTypeNative_rasterize(
        JNIEnv* env, jclass, jbyteArray fontData, jint fontDataSize, jintArray codePoints, jint codePointCount,
        jfloat pixelSize, jint padding, jint atlasWidth, jintArray metricInts, jfloatArray metricFloats,
        jobject rgba, jint rgbaSize, jintArray glyphInts, jint glyphIntCount, jfloatArray glyphFloats,
        jint glyphFloatCount, jintArray kerningInts, jint kerningIntCount) {
    if (fontData == nullptr || codePoints == nullptr || metricInts == nullptr || metricFloats == nullptr) {
        return 0;
    }

    void* rgbaPtr = nullptr;
    if (rgba != nullptr && rgbaSize > 0) {
        rgbaPtr = env->GetDirectBufferAddress(rgba);
        if (rgbaPtr == nullptr) {
            jclass exceptionClass = env->FindClass("java/lang/IllegalArgumentException");
            if (exceptionClass != nullptr) {
                env->ThrowNew(exceptionClass, "FreeType RGBA target must be a direct ByteBuffer");
            }
            return 0;
        }
    }

    int8_t* fontDataPtr = reinterpret_cast<int8_t*>(env->GetByteArrayElements(fontData, nullptr));
    int32_t* codePointsPtr = reinterpret_cast<int32_t*>(env->GetIntArrayElements(codePoints, nullptr));
    int32_t* metricIntsPtr = reinterpret_cast<int32_t*>(env->GetIntArrayElements(metricInts, nullptr));
    float* metricFloatsPtr = reinterpret_cast<float*>(env->GetFloatArrayElements(metricFloats, nullptr));
    int32_t* glyphIntsPtr = glyphInts != nullptr
            ? reinterpret_cast<int32_t*>(env->GetIntArrayElements(glyphInts, nullptr))
            : nullptr;
    float* glyphFloatsPtr = glyphFloats != nullptr
            ? reinterpret_cast<float*>(env->GetFloatArrayElements(glyphFloats, nullptr))
            : nullptr;
    int32_t* kerningIntsPtr = kerningInts != nullptr
            ? reinterpret_cast<int32_t*>(env->GetIntArrayElements(kerningInts, nullptr))
            : nullptr;

    int32_t actualFontSize = std::min<int32_t>(fontDataSize, env->GetArrayLength(fontData));
    int32_t result = fdx_freetype_rasterize(fontDataPtr, actualFontSize, codePointsPtr, codePointCount,
            pixelSize, padding, atlasWidth, metricIntsPtr, metricFloatsPtr, rgbaPtr, rgbaSize,
            glyphIntsPtr, glyphIntCount, glyphFloatsPtr, glyphFloatCount, kerningIntsPtr, kerningIntCount);

    if (kerningIntsPtr != nullptr) {
        env->ReleaseIntArrayElements(kerningInts, reinterpret_cast<jint*>(kerningIntsPtr), 0);
    }
    if (glyphFloatsPtr != nullptr) {
        env->ReleaseFloatArrayElements(glyphFloats, glyphFloatsPtr, 0);
    }
    if (glyphIntsPtr != nullptr) {
        env->ReleaseIntArrayElements(glyphInts, reinterpret_cast<jint*>(glyphIntsPtr), 0);
    }
    env->ReleaseFloatArrayElements(metricFloats, metricFloatsPtr, 0);
    env->ReleaseIntArrayElements(metricInts, reinterpret_cast<jint*>(metricIntsPtr), 0);
    env->ReleaseIntArrayElements(codePoints, reinterpret_cast<jint*>(codePointsPtr), JNI_ABORT);
    env->ReleaseByteArrayElements(fontData, reinterpret_cast<jbyte*>(fontDataPtr), JNI_ABORT);
    return result;
}
