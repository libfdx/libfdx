package io.github.libfdx.backend.desktop;

import io.github.libfdx.math.Matrix4;
import io.github.libfdx.math.internal.MathAcceleration;

/**
 * Represents a desktop math acceleration check.
 *
 * @author xpenatan
 */
public final class DesktopMathAccelerationCheck {
    private static final float TOLERANCE = 0.00001f;

    private DesktopMathAccelerationCheck() {
    }

    /**
     * Runs the launcher entry point.
     *
     * @param args the args
     */
    public static void main(String[] args) {
        boolean requireNative = Boolean.parseBoolean(System.getProperty("libfdx.math.requireNative", "false"));
        MathAcceleration.register(new DesktopMathAccelerator());
        boolean nativeAvailable = MathAcceleration.available();
        if (requireNative && !nativeAvailable) {
            throw new AssertionError("Desktop SIMD math is unavailable: "
                    + DesktopMathAccelerator.diagnostic());
        }
        String accelerationName = DesktopMathAccelerator.accelerationName();

        checkMatrixMultiply(nativeAvailable);
        checkTransformPositions(nativeAvailable);
        MathAcceleration.register(null);
        System.out.println("[info] DesktopMathAccelerationCheck passed, nativeAvailable=" + nativeAvailable
                + ", acceleration=" + accelerationName);
    }

    private static void checkMatrixMultiply(boolean nativeAvailable) {
        MathAcceleration.register(null);
        Matrix4 left = Matrix4.translation(2.0f, -3.0f, 4.0f)
                .mul(Matrix4.rotationQuaternion(0.2f, -0.3f, 0.4f, 0.8f))
                .mul(Matrix4.scale(1.5f, 0.5f, -2.0f));
        Matrix4 right = Matrix4.rotationY(0.35f).mul(Matrix4.translation(-7.0f, 2.0f, 5.0f));

        float[] scalar = new Matrix4().setToMul(left, right).values();

        MathAcceleration.register(new DesktopMathAccelerator());
        float[] accelerated = new Matrix4().setToMul(left, right).values();
        compare("matrix4Mul", scalar, accelerated);

        if (nativeAvailable && !MathAcceleration.available()) {
            throw new AssertionError("Desktop SIMD math was disabled during matrix multiply");
        }
    }

    private static void checkTransformPositions(boolean nativeAvailable) {
        MathAcceleration.register(null);
        Matrix4 transform = Matrix4.translation(1.25f, -2.5f, 3.75f)
                .mul(Matrix4.rotationZ(0.65f))
                .mul(Matrix4.scale(0.75f, 1.5f, -1.25f));
        float[] source = new float[64];
        for (int i = 0; i < source.length; i++) {
            source[i] = (float)((i % 11) - 5) * 0.375f;
        }
        float[] scalar = source.clone();
        float[] accelerated = source.clone();

        transform.transformPositions(scalar, 2, 10, 5);

        MathAcceleration.register(new DesktopMathAccelerator());
        transform.transformPositions(accelerated, 2, 10, 5);
        compare("matrix4TransformPositions", scalar, accelerated);

        if (nativeAvailable && !MathAcceleration.available()) {
            throw new AssertionError("Desktop SIMD math was disabled during position transform");
        }
    }

    private static void compare(String label, float[] expected, float[] actual) {
        if (expected.length != actual.length) {
            throw new AssertionError(label + " length mismatch");
        }
        for (int i = 0; i < expected.length; i++) {
            float difference = Math.abs(expected[i] - actual[i]);
            if (difference > TOLERANCE) {
                throw new AssertionError(label + " mismatch at " + i + ": expected=" + expected[i]
                        + ", actual=" + actual[i] + ", difference=" + difference);
            }
        }
    }
}
