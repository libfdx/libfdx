package io.github.libfdx.backend.desktop;

import io.github.libfdx.math.internal.MathAccelerator;

final class DesktopNativeMathAccelerator implements MathAccelerator {
    private static final boolean LOADED = DesktopRuntimeCoreNative.load();
    private static final boolean SIMD_AVAILABLE = detectSimdAvailable();

    @Override
    public boolean available() {
        return SIMD_AVAILABLE;
    }

    @Override
    public boolean matrix4Mul(float[] left, float[] right, float[] out) {
        return SIMD_AVAILABLE && nativeMatrix4Mul(left, right, out);
    }

    @Override
    public boolean matrix4TransformPositions(float[] matrix, float[] values, int offset, int count, int stride) {
        return SIMD_AVAILABLE && nativeMatrix4TransformPositions(matrix, values, offset, count, stride);
    }

    static String diagnostic() {
        if (SIMD_AVAILABLE) {
            return "desktop SIMD math available: " + accelerationName();
        }
        String failure = DesktopRuntimeCoreNative.failureMessage();
        return failure != null ? failure : "desktop SIMD math unavailable";
    }

    static String accelerationName() {
        if (!LOADED) {
            return "unloaded";
        }
        try {
            return nativeAccelerationName();
        } catch (UnsatisfiedLinkError error) {
            return "unknown";
        }
    }

    private static boolean detectSimdAvailable() {
        if (!LOADED) {
            return false;
        }
        try {
            return nativeSimdAvailable();
        } catch (UnsatisfiedLinkError error) {
            return false;
        }
    }

    private static native boolean nativeSimdAvailable();

    private static native String nativeAccelerationName();

    private static native boolean nativeMatrix4Mul(float[] left, float[] right, float[] out);

    private static native boolean nativeMatrix4TransformPositions(float[] matrix, float[] values, int offset,
            int count, int stride);
}
