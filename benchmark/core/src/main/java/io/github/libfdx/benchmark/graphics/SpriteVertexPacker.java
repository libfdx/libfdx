package io.github.libfdx.benchmark.graphics;

/** Desktop benchmark kernel for a batch of interleaved x, y and rotation triples. */
@FunctionalInterface
public interface SpriteVertexPacker {
    /** Advances rotations and writes positions into preinitialized 20-float sprite records. */
    void pack(float[] sprites, float[] vertices, float[] sineTable, float rotationStep, float scale);
}
