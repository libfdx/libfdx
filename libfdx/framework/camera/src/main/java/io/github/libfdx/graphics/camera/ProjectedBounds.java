package io.github.libfdx.graphics.camera;

import io.github.libfdx.math.BoundingBox;
import io.github.libfdx.math.ClipDepthRange;
import io.github.libfdx.math.Matrix4;

/**
 * Application-owned, allocation-free projected extent of conservative bounds.
 * Call update after camera/viewport changes. Bounds crossing the eye/near plane,
 * unknown bounds and invalid transforms return positive infinity (full detail).
 * This measures the larger projected width/height, not visibility or occlusion.
 * Single-thread confined; retains no camera, frame or bounds references.
 */
public final class ProjectedBounds {
    private final float[] projection = new float[16];
    private final float[] view = new float[16];
    private final float[] world = new float[16];
    private final double[] combined = new double[16];
    private double eyeX, eyeY, eyeZ;
    private float width, height;
    private ClipDepthRange depthRange;
    private boolean ready;

    /**
     * Snapshots an updated camera, treating its declared viewport dimensions as pixels.
     * Use the explicit pixel-size overload when an orthographic viewport uses world units.
     */
    public ProjectedBounds update(Camera camera) {
        if (camera == null) {
            ready = false;
            throw new IllegalArgumentException("Projected-bounds camera cannot be null");
        }
        return update(camera, camera.viewportWidth(), camera.viewportHeight());
    }

    /**
     * Snapshots an updated camera and actual render-viewport pixel dimensions.
     * Subtracts the eye before projection to preserve local detail in far worlds.
     * Invalid input throws and invalidates the previous snapshot.
     */
    public ProjectedBounds update(Camera camera, float pixelWidth, float pixelHeight) {
        ready = false;
        if (camera == null) throw new IllegalArgumentException("Camera cannot be null");
        viewport(pixelWidth, pixelHeight, camera.clipDepthRange());
        camera.projectionMatrix().copyValues(projection, 0);
        camera.view().copyValues(view, 0);
        eyeX = camera.position().x(); eyeY = camera.position().y(); eyeZ = camera.position().z();
        if (!Double.isFinite(eyeX) || !Double.isFinite(eyeY) || !Double.isFinite(eyeZ))
            throw new IllegalArgumentException("Camera eye must be finite");
        for (int column = 0; column < 4; column++) {
            for (int row = 0; row < 4; row++) {
                double value = column == 3 ? projection[12 + row] : 0;
                if (column != 3) for (int k = 0; k < 3; k++)
                    value += projection[k * 4 + row] * (double)view[column * 4 + k];
                if (!Double.isFinite(value)) throw new IllegalArgumentException("Camera matrices must be finite");
                combined[column * 4 + row] = value;
            }
        }
        ready = true;
        return this;
    }

    /**
     * Snapshots an explicit world-to-clip matrix, e.g. a camera-relative view.
     * Bounds and their transform must use that matrix's coordinate frame.
     */
    public ProjectedBounds update(Matrix4 worldToClip, ClipDepthRange range,
            float pixelWidth, float pixelHeight) {
        ready = false;
        if (worldToClip == null) throw new IllegalArgumentException("Projection cannot be null");
        viewport(pixelWidth, pixelHeight, range);
        worldToClip.copyValues(projection, 0);
        for (int i = 0; i < 16; i++) {
            if (!Float.isFinite(projection[i])) throw new IllegalArgumentException("Projection must be finite");
            combined[i] = projection[i];
        }
        eyeX = eyeY = eyeZ = 0;
        ready = true;
        return this;
    }

    private void viewport(float pixelWidth, float pixelHeight, ClipDepthRange range) {
        if (range == null || !Float.isFinite(pixelWidth) || !Float.isFinite(pixelHeight)
                || pixelWidth <= 0 || pixelHeight <= 0)
            throw new IllegalArgumentException("Viewport must be finite and positive, with a clip-depth range");
        width = pixelWidth; height = pixelHeight; depthRange = range;
    }

    /** Measures world-space bounds. Null/unknown bounds conservatively return infinity. */
    public float diameterPixels(BoundingBox bounds) { return diameterPixels(bounds, null); }

    /**
     * Measures local bounds after an affine transform (null means identity).
     * Use full-detail, current-pose bounds, including shader displacement.
     * Negative/nonuniform scale and shear are supported. Does not clamp to the
     * viewport or cull offscreen objects. Requires a successful update first.
     */
    public float diameterPixels(BoundingBox bounds, Matrix4 transform) {
        if (!ready) throw new IllegalStateException("Projected bounds must be updated first");
        if (bounds == null || bounds.min() == null || bounds.max() == null) return Float.POSITIVE_INFINITY;
        var min = bounds.min(); var max = bounds.max();
        if (!finite(min.x(), min.y(), min.z()) || !finite(max.x(), max.y(), max.z())
                || min.x() > max.x() || min.y() > max.y() || min.z() > max.z()) return Float.POSITIVE_INFINITY;
        (transform == null ? Matrix4.IDENTITY : transform).copyValues(world, 0);
        for (int i = 0; i < 16; i++) if (!Float.isFinite(world[i])) return Float.POSITIVE_INFINITY;
        if (world[3] != 0 || world[7] != 0 || world[11] != 0 || world[15] != 1) return Float.POSITIVE_INFINITY;
        double minX = Double.POSITIVE_INFINITY, minY = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY, maxY = Double.NEGATIVE_INFINITY;
        for (int corner = 0; corner < 8; corner++) {
            double x = (corner & 1) == 0 ? min.x() : max.x();
            double y = (corner & 2) == 0 ? min.y() : max.y();
            double z = (corner & 4) == 0 ? min.z() : max.z();
            double wx = (world[12] - eyeX) + world[0] * x + world[4] * y + world[8] * z;
            double wy = (world[13] - eyeY) + world[1] * x + world[5] * y + world[9] * z;
            double wz = (world[14] - eyeZ) + world[2] * x + world[6] * y + world[10] * z;
            double cx = combined[0] * wx + combined[4] * wy + combined[8] * wz + combined[12];
            double cy = combined[1] * wx + combined[5] * wy + combined[9] * wz + combined[13];
            double cz = combined[2] * wx + combined[6] * wy + combined[10] * wz + combined[14];
            double cw = combined[3] * wx + combined[7] * wy + combined[11] * wz + combined[15];
            double near = depthRange == ClipDepthRange.ZERO_TO_ONE_REVERSED ? cw - cz
                    : depthRange == ClipDepthRange.ZERO_TO_ONE ? cz : cz + cw;
            double tolerance = Math.max(1e-8, Math.abs(cw) * 1e-6);
            if (!Double.isFinite(cx) || !Double.isFinite(cy) || !Double.isFinite(cz)
                    || !Double.isFinite(cw) || cw <= tolerance || near <= tolerance) return Float.POSITIVE_INFINITY;
            double px = cx / cw, py = cy / cw;
            minX = Math.min(minX, px); maxX = Math.max(maxX, px);
            minY = Math.min(minY, py); maxY = Math.max(maxY, py);
        }
        double result = Math.max((maxX - minX) * width * .5, (maxY - minY) * height * .5);
        return Double.isFinite(result) && result <= Float.MAX_VALUE ? (float)result : Float.POSITIVE_INFINITY;
    }

    private static boolean finite(float x, float y, float z) {
        return Float.isFinite(x) && Float.isFinite(y) && Float.isFinite(z);
    }
}
