#include <jni.h>
#include <cstdint>

#include "libfdx_math.h"

extern "C" JNIEXPORT jboolean JNICALL
Java_io_github_libfdx_backend_android_AndroidNativeMathAccelerator_nativeSimdAvailable(JNIEnv*, jclass) {
    return fdx_math_simd_available() != 0 ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jstring JNICALL
Java_io_github_libfdx_backend_android_AndroidNativeMathAccelerator_nativeAccelerationName(JNIEnv* env, jclass) {
    return env->NewStringUTF(fdx_math_acceleration_name());
}

extern "C" JNIEXPORT jboolean JNICALL
Java_io_github_libfdx_backend_android_AndroidNativeMathAccelerator_nativeMatrix4Mul(JNIEnv* env, jclass,
        jfloatArray left_array, jfloatArray right_array, jfloatArray out_array) {
    if (left_array == nullptr || right_array == nullptr || out_array == nullptr
            || env->GetArrayLength(left_array) < 16
            || env->GetArrayLength(right_array) < 16
            || env->GetArrayLength(out_array) < 16) {
        return JNI_FALSE;
    }

    float left[16];
    float right[16];
    float out[16];
    env->GetFloatArrayRegion(left_array, 0, 16, left);
    if (env->ExceptionCheck()) {
        return JNI_FALSE;
    }
    env->GetFloatArrayRegion(right_array, 0, 16, right);
    if (env->ExceptionCheck()) {
        return JNI_FALSE;
    }
    if (fdx_math_matrix4_mul(left, right, out) == 0) {
        return JNI_FALSE;
    }
    env->SetFloatArrayRegion(out_array, 0, 16, out);
    if (env->ExceptionCheck()) {
        return JNI_FALSE;
    }
    return JNI_TRUE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_io_github_libfdx_backend_android_AndroidNativeMathAccelerator_nativeMatrix4TransformPositions(JNIEnv* env,
        jclass, jfloatArray matrix_array, jfloatArray values_array, jint offset, jint count, jint stride) {
    if (matrix_array == nullptr || values_array == nullptr || env->GetArrayLength(matrix_array) < 16
            || offset < 0 || count < 0 || stride < 3) {
        return JNI_FALSE;
    }

    if (count > 0) {
        jlong last_index = static_cast<jlong>(offset) + (static_cast<jlong>(count) - 1) * stride + 2;
        if (last_index >= env->GetArrayLength(values_array)) {
            return JNI_FALSE;
        }
    }

    float matrix[16];
    env->GetFloatArrayRegion(matrix_array, 0, 16, matrix);
    if (env->ExceptionCheck()) {
        return JNI_FALSE;
    }

    jboolean copied = JNI_FALSE;
    jfloat* values = static_cast<jfloat*>(env->GetPrimitiveArrayCritical(values_array, &copied));
    if (values == nullptr) {
        return JNI_FALSE;
    }
    int32_t result = fdx_math_matrix4_transform_positions(matrix, values, offset, count, stride);
    env->ReleasePrimitiveArrayCritical(values_array, values, 0);
    return result != 0 ? JNI_TRUE : JNI_FALSE;
}
