package io.github.libfdx.graphics.meshoptimizer;

/** Actual level statistics, including repeated node draws. A quality limit may prevent the requested ratio. */
public record ModelLodReport(int level, int sourceTriangles, int triangles, int vertices, int drawParts,
        float error, boolean targetReached) {}
