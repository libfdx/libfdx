package io.github.libfdx.graphics.g3d;

import io.github.libfdx.math.BoundingBox;

/**
 * Completed indexed CPU geometry from {@link ModelBuilder#shapes()}.
 * Positions/normals are XYZ, colors are linear RGBA, and indices are unsigned
 * 16-bit values. A part has at most 65,536 vertices. Material is a caller-supplied
 * slot name, not a GPU resource. Arrays and bounds belong to this result and
 * are borrowed read-only; copy before editing. No disposal or graphics context
 * is required. Publish only after the builder has finished.
 */
public record ModelShapePart(String material, float[] positions, float[] normals,
        float[] colors, short[] indices, BoundingBox bounds) {
}
