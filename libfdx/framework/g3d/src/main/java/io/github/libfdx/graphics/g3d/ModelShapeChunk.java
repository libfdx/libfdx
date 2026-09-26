package io.github.libfdx.graphics.g3d;

import io.github.libfdx.collections.FloatArray;
import io.github.libfdx.collections.IntArray;
import io.github.libfdx.core.FdxException;
import io.github.libfdx.math.BoundingBox;
import io.github.libfdx.math.Vector3;

/** Mutable builder storage. Never exposed to consumers of a completed part. */
final class ModelShapeChunk {
    private final String material;
    private final FloatArray positions = new FloatArray();
    private final FloatArray normals = new FloatArray();
    private final FloatArray colors = new FloatArray();
    private final IntArray indices = new IntArray();
    private final BoundingBox bounds = new BoundingBox(
            new Vector3(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY),
            new Vector3(Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY));

    ModelShapeChunk(String material) { this.material = material; }
    int vertices() { return positions.size() / 3; }

    void face(float[][] points, float[] color, float[][] suppliedNormals) {
        Vector3 normal = new Vector3(points[1][0] - points[0][0],
                points[1][1] - points[0][1], points[1][2] - points[0][2]);
        normal = normal.cross(new Vector3(points[2][0] - points[0][0],
                points[2][1] - points[0][1], points[2][2] - points[0][2]));
        float length = normal.length();
        if(!Float.isFinite(length) || length < 1e-10f)
            throw new FdxException("Degenerate or out-of-range model face");
        normal = normal.normalize();
        // Validate before modifying this chunk, so rejected input cannot leave a partial face.
        for(float[] point : points) for(float value : point)
            if(!Float.isFinite(value)) throw new FdxException("Model positions must be finite");
        int base = vertices();
        for(int i = 0; i < points.length; i++) {
            float[] point = points[i];
            for(float value : point) positions.add(value);
            if(suppliedNormals == null) {
                normals.add(normal.x()); normals.add(normal.y()); normals.add(normal.z());
            } else for(float value : suppliedNormals[i]) normals.add(value);
            for(float value : color) colors.add(value);
            bounds.min().set(Math.min(bounds.min().x(), point[0]),
                    Math.min(bounds.min().y(), point[1]), Math.min(bounds.min().z(), point[2]));
            bounds.max().set(Math.max(bounds.max().x(), point[0]),
                    Math.max(bounds.max().y(), point[1]), Math.max(bounds.max().z(), point[2]));
        }
        indices.add(base); indices.add(base + 1); indices.add(base + 2);
        if(points.length == 4) { indices.add(base); indices.add(base + 2); indices.add(base + 3); }
    }

    ModelShapePart finish() {
        short[] packed = new short[indices.size()];
        for(int i = 0; i < packed.length; i++) packed[i] = (short)indices.get(i);
        return new ModelShapePart(material, positions.toArray(), normals.toArray(),
                colors.toArray(), packed, bounds);
    }
}
