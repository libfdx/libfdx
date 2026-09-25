package io.github.libfdx.graphics.g3d.lod;

import io.github.libfdx.graphics.g3d.ModelInstance;

/**
 * Borrowed, application-owned ready model instances for one logical object.
 * Methods run on the rendering thread and must not block, load, allocate geometry
 * or submit GPU work. Publish loading results between render submissions.
 * Levels share coordinates/pivots and represent the same object at lower detail.
 */
public interface ModelLodSource {
    /** Fixed level count, including level zero, matching the binding configuration. */
    int levelCount();
    /**
     * Borrowed ready instance, or null for an unavailable reduced level. Level zero
     * is always non-null and retains its identity for the binding's lifetime.
     */
    ModelInstance model(int level);
    /**
     * Makes a ready reduced level match the current base pose/material state.
     * Return false if this cannot be done faithfully. Do not mutate the base.
     * Root transforms are copied by the binding after success. Called only for
     * non-null levels greater than zero. No instance may already be queued while
     * its state is being changed. See ModelLodModels for a conservative default.
     */
    boolean synchronize(int level);
}
