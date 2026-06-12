package io.github.libfdx.math.internal;

/**
 * Represents a math acceleration.
 *
 * @author xpenatan
 */
public final class MathAcceleration {
    private static volatile MathAccelerator accelerator;

    private MathAcceleration() {
    }

    /**
     * Runs the register step.
     *
     * @param mathAccelerator the math accelerator
     */
    public static void register(MathAccelerator mathAccelerator) {
        accelerator = mathAccelerator != null && mathAccelerator.available() ? mathAccelerator : null;
    }

    /**
     * Returns the available.
     *
     * @return true if available succeeds or is active; false otherwise
     */
    public static boolean available() {
        MathAccelerator current = accelerator;
        return current != null && current.available();
    }

    /**
     * Runs the matrix4 mul step.
     *
     * @param left the left
     * @param right the right
     * @param out the out
     * @return true if matrix4 mul succeeds or is active; false otherwise
     */
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
