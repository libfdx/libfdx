#include <jni.h>
#include "sprite_vertices.hpp"

namespace {
void packSprites(float* sprites, float* vertices, const float* sine, int spriteCount,
        float rotationStep, float scale) {
    const float local = -16.0f * scale;
    const float far = 16.0f * scale;
    for (int i = 0; i < spriteCount; ++i, sprites += 3, vertices += 20) {
        // The benchmark starts in [0, 360) and supplies a fixed step below one degree.
        float rotation = sprites[2] + rotationStep;
        if (rotation >= 360.0f) rotation -= 360.0f;
        sprites[2] = rotation;
        sprite_vertices::writePositions(vertices, sprites[0] + 16.0f, sprites[1] + 16.0f,
                rotation, local, local, far, far, sine);
    }
}
}

extern "C" JNIEXPORT jboolean JNICALL
Java_io_github_libfdx_benchmark_desktop_DesktopBenchmarkLauncher_packSpritesNative(
        JNIEnv* env, jclass, jfloatArray sprites, jfloatArray vertices, jfloatArray sine,
        jfloat rotationStep, jfloat scale) {
    const jsize inputLength = env->GetArrayLength(sprites);
    const int spriteCount = inputLength / 3;
    if (inputLength % 3 != 0 || spriteCount > 8191 || env->GetArrayLength(vertices) < spriteCount * 20
            || env->GetArrayLength(sine) != sprite_vertices::sineCount) {
        return JNI_FALSE;
    }

    auto* input = static_cast<jfloat*>(env->GetPrimitiveArrayCritical(sprites, nullptr));
    if (input == nullptr) return JNI_FALSE;
    auto* output = static_cast<jfloat*>(env->GetPrimitiveArrayCritical(vertices, nullptr));
    if (output == nullptr) {
        env->ReleasePrimitiveArrayCritical(sprites, input, JNI_ABORT);
        return JNI_FALSE;
    }
    auto* table = static_cast<jfloat*>(env->GetPrimitiveArrayCritical(sine, nullptr));
    if (table != nullptr) {
        packSprites(input, output, table, spriteCount, rotationStep, scale);
        env->ReleasePrimitiveArrayCritical(sine, table, JNI_ABORT);
    }
    env->ReleasePrimitiveArrayCritical(vertices, output, table != nullptr ? 0 : JNI_ABORT);
    env->ReleasePrimitiveArrayCritical(sprites, input, table != nullptr ? 0 : JNI_ABORT);
    return table != nullptr ? JNI_TRUE : JNI_FALSE;
}
