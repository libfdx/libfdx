package io.github.libfdx.math.internal;

public interface MathAccelerator {
    boolean available();

    boolean matrix4Mul(float[] left, float[] right, float[] out);

    boolean matrix4TransformPositions(float[] matrix, float[] values, int offset, int count, int stride);
}
