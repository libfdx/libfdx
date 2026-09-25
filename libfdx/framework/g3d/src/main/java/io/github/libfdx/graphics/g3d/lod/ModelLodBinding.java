package io.github.libfdx.graphics.g3d.lod;

import io.github.libfdx.graphics.camera.ProjectedBounds;
import io.github.libfdx.graphics.g3d.ModelInstance;
import io.github.libfdx.math.BoundingBox;

/**
 * Explicit, application-owned LOD selection before ordinary ModelBatch rendering.
 * Borrows its source/base and never loads or disposes GPU resources. Single-thread
 * confined. Selection reuses state and does not allocate. Keep a ModelLodView for
 * each instance/camera/viewport/pass. Retain the base for authored state and picking.
 * Finish using queued instances before changing their poses/materials or resources.
 */
public final class ModelLodBinding {
    private final ModelLodSource source;
    private final ModelInstance base;
    private ModelLodConfig config;
    private boolean enabled = true;

    /** Source count must match the configuration; the base must already be ready. */
    public ModelLodBinding(ModelLodConfig config, ModelLodSource source) {
        if (source == null || source.levelCount() < 1 || source.model(0) == null)
            throw new IllegalArgumentException("LOD source must have a ready base model");
        this.source = source;
        base = source.model(0);
        config(config);
    }
    /** Replaces immutable configuration with the same number of levels; view state resets on its next selection. */
    public ModelLodBinding config(ModelLodConfig config) {
        if (config == null || config.levelCount() != source.levelCount())
            throw new IllegalArgumentException("LOD configuration and source counts must match");
        this.config = config; return this;
    }
    /** Current immutable configuration. */
    public ModelLodConfig config() { return config; }
    /** Disabling selects the full-detail base; authored levels remain available. */
    public ModelLodBinding enabled(boolean enabled) { this.enabled = enabled; return this; }
    /** Whether automatic detail selection is enabled. */
    public boolean enabled() { return enabled; }
    /** Borrowed, stable base instance; use it for authored state/picking. */
    public ModelInstance base() { return base; }

    /**
     * Chooses using full-detail bounds local to the base instance's root transform.
     * Bounds must include current pose/deformation. Null/unknown means full detail.
     */
    public ModelInstance select(ModelLodView state, ProjectedBounds projection, BoundingBox localBounds) {
        if (projection == null) throw new IllegalArgumentException("LOD projection cannot be null");
        return select(state, enabled ? projection.diameterPixels(localBounds, base.transform()) : Float.POSITIVE_INFINITY);
    }

    /** Chooses using a caller-computed conservative projected diameter in pixels. */
    public ModelInstance select(ModelLodView state, float projectedPixels) {
        if (state == null) throw new IllegalArgumentException("LOD view state cannot be null");
        if (!enabled) { state.force(0); return resolve(state, 0); }
        return resolve(state, state.select(config, projectedPixels));
    }

    /**
     * Explicit preview/pass choice, bypassing automatic selection and the enabled
     * flag but retaining availability/compatibility fallback. Use a separate preview
     * state to leave game-camera hysteresis untouched. The next automatic call resets.
     */
    public ModelInstance selectLevel(ModelLodView state, int level) {
        if (state == null) throw new IllegalArgumentException("LOD view state cannot be null");
        if (level < 0 || level >= config.levelCount()) throw new IndexOutOfBoundsException("LOD level: " + level);
        state.force(level);
        return resolve(state, level);
    }

    private ModelInstance resolve(ModelLodView state, int desired) {
        if (source.levelCount() != config.levelCount() || source.model(0) != base)
            throw new IllegalStateException("Recreate the LOD binding after changing its base or level count");
        ModelLodFallback reason = ModelLodFallback.NONE;
        for (int level = desired; level > 0; level--) {
            ModelInstance candidate = source.model(level);
            if (candidate == null) {
                if (level == desired) reason = ModelLodFallback.UNAVAILABLE;
            } else if (!source.synchronize(level)) {
                if (level == desired) reason = ModelLodFallback.INCOMPATIBLE;
            } else {
                candidate.transform().set(base.transform());
                state.resolved(level, reason);
                return candidate;
            }
        }
        state.resolved(0, reason);
        return base;
    }
}
