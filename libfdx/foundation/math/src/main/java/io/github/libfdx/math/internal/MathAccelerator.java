package io.github.libfdx.math.internal;

/**
 * Defines the contract for math accelerator implementations.
 *
 * @author xpenatan
 */
public interface MathAccelerator {
    /**
     * Returns the available.
     *
     * @return true if available succeeds or is active; false otherwise
     */
    boolean available();

    /**
     * Runs the matrix4 mul step.
     *
     * @param left the left
     * @param right the right
     * @param out the out
     * @return true if matrix4 mul succeeds or is active; false otherwise
     */
    boolean matrix4Mul(float[] left, float[] right, float[] out);

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
    boolean matrix4TransformPositions(float[] matrix, float[] values, int offset, int count, int stride);
}
