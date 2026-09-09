package io.github.libfdx.graphics.g3d;

import io.github.libfdx.core.FdxException;
import io.github.libfdx.graphics.GraphicsContext;

/** Immutable setup budget for cascaded shadows. The byte estimate covers RGBA8 packed depth and
 * the logical DEPTH32 attachment, excluding provider padding, frame retention and renderer storage.
 * No automatic quality downgrade is performed. Created maps are caller-owned. */
public final class ShadowBudget3D {
    public static final ShadowBudget3D LOW = new ShadowBudget3D(1, 512, 40, 2L << 20);
    public static final ShadowBudget3D BALANCED = new ShadowBudget3D(2, 1024, 80, 16L << 20);
    public static final ShadowBudget3D HIGH = new ShadowBudget3D(4, 2048, 160, 128L << 20);
    private final int cascadeCount, resolution;
    private final float maxDistance;
    private final long maxBytes, estimatedBytes;

    /** Rejects invalid dimensions or budgets before allocating resources. Resolution is 1..8192,
     * cascade count 1..4 and distance finite/positive. Device limits are checked by texture creation. */
    public ShadowBudget3D(int cascadeCount, int resolution, float maxDistance, long maxBytes) {
        if (cascadeCount < 1 || cascadeCount > 4 || resolution < 1 || resolution > 8192
                || !Float.isFinite(maxDistance) || maxDistance <= 0 || maxBytes <= 0) {
            throw new FdxException("Invalid cascaded shadow budget");
        }
        long bytes = (long) cascadeCount * resolution * resolution * 8;
        if (bytes > maxBytes) throw new FdxException("Shadow allocation estimate exceeds its byte budget");
        this.cascadeCount=cascadeCount; this.resolution=resolution; this.maxDistance=maxDistance;
        this.maxBytes=maxBytes; estimatedBytes=bytes;
    }
    /** Allocates maps now on the graphics thread. Unsupported capabilities or allocation failures propagate. */
    public CascadedShadowMap3D create(GraphicsContext graphics) {
        return new CascadedShadowMap3D(graphics,cascadeCount,resolution,resolution).maxDistance(maxDistance);
    }
    public int cascadeCount() { return cascadeCount; }
    public int resolution() { return resolution; }
    public float maxDistance() { return maxDistance; }
    public long maxBytes() { return maxBytes; }
    public long estimatedBytes() { return estimatedBytes; }
}
