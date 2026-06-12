package io.github.libfdx.backend.desktop;

import io.github.libfdx.math.internal.MathAccelerator;

/**
 * Represents a desktop native math accelerator.
 *
 * @author xpenatan
 */
final class DesktopNativeMathAccelerator implements MathAccelerator {
    private static final boolean LOADED = DesktopRuntimeCoreNative.load();
    private static final boolean SIMD_AVAILABLE = detectSimdAvailable();

    /**
     * Returns the available.
     *
     * @return true if available succeeds or is active; false otherwise
     */
    @Override
    public boolean available() {
        return SIMD_AVAILABLE;
    }

    /**
     * Runs the matrix4 mul step.
     *
     * @param left the left
     * @param right the right
     * @param out the out
     * @return true if matrix4 mul succeeds or is active; false otherwise
     */
    @Override
    public boolean matrix4Mul(float[] left, float[] right, float[] out) {
        return SIMD_AVAILABLE && nativeMatrix4Mul(left, right, out);
    }

    /**
     * Runs the matrix4 transform positions step.
     *
     * @param matrix the matrix
     * @param values the values
     * @param offset the offset
     * @param count the count
     * @param stride the stride
     * @return true if matrix4 transform positions succeeds or is active; false otherwise
     */
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
