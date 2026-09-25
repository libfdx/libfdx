package io.github.libfdx.graphics.meshoptimizer;

/** One reduced level: ratio of original triangles, relative simplification error and pixel threshold. */
public record ModelLodTarget(float triangleRatio, float maxError, float maxScreenPixels) {
    public ModelLodTarget {
        if (!Float.isFinite(triangleRatio) || triangleRatio <= 0 || triangleRatio >= 1)
            throw new IllegalArgumentException("Triangle ratio must be between zero and one, exclusively");
        if (!Float.isFinite(maxError) || maxError < 0 || maxError > 1)
            throw new IllegalArgumentException("Relative error must be between zero and one");
        if (!Float.isFinite(maxScreenPixels) || maxScreenPixels <= 0)
            throw new IllegalArgumentException("Pixel threshold must be finite and positive");
    }
}
