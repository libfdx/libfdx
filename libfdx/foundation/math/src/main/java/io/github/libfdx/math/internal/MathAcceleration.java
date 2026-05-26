package io.github.libfdx.math.internal;

public final class MathAcceleration {
    private static volatile MathAccelerator accelerator;

    private MathAcceleration() {
    }

    public static void register(MathAccelerator mathAccelerator) {
        accelerator = mathAccelerator != null && mathAccelerator.available() ? mathAccelerator : null;
    }

    public static boolean available() {
        MathAccelerator current = accelerator;
        return current != null && current.available();
    }

    public static boolean matrix4Mul(float[] left, float[] right, float[] out) {
        MathAccelerator current = accelerator;
        if (current == null) {
            return false;
        }
        try {
            return current.matrix4Mul(left, right, out);
        } catch (RuntimeException | LinkageError error) {
            accelerator = null;
            return false;
        }
    }

    public static boolean matrix4TransformPositions(float[] matrix, float[] values, int offset, int count,
            int stride) {
        MathAccelerator current = accelerator;
        if (current == null) {
            return false;
        }
        try {
            return current.matrix4TransformPositions(matrix, values, offset, count, stride);
        } catch (RuntimeException | LinkageError error) {
            accelerator = null;
            return false;
        }
    }
}
