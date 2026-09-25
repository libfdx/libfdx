package io.github.libfdx.graphics.g3d.lod;

import io.github.libfdx.graphics.g3d.ModelInstance;

/** Optional application-specific pose/material mapping, replacing the static compatibility guard. */
@FunctionalInterface
public interface ModelLodSynchronizer {
    /**
     * Update the candidate's compatible node/material state and return true, or
     * return false to use higher detail. Both instances are borrowed. Never change
     * the base, their shared model assets or state already queued for rendering.
     * Called on the rendering thread; must not load or allocate geometry.
     * The binding copies the root transform after success.
     */
    boolean synchronize(ModelInstance base, ModelInstance candidate, int level);
}
