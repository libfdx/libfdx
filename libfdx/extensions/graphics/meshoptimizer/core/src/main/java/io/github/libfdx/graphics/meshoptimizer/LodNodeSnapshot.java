package io.github.libfdx.graphics.meshoptimizer;

import io.github.libfdx.math.Matrix4;

record LodNodeSnapshot(String id, Matrix4 transform, LodPartSnapshot[] parts, LodNodeSnapshot[] children, float[] morphWeights) {}
