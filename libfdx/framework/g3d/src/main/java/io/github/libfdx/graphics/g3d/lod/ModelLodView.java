package io.github.libfdx.graphics.g3d.lod;

/**
 * Reusable, single-thread-confined state for one instance and one camera/viewport/pass.
 * Applications own this object. Do not share it between objects or simultaneous views.
 * No camera or model is retained; separate views can share immutable configuration.
 */
public final class ModelLodView {
    private ModelLodConfig config;
    private int desired, rendered;
    private boolean initialized;
    private ModelLodFallback fallback = ModelLodFallback.NONE;

    /**
     * Chooses a level without loading or inspecting model resources. Invalid/unknown
     * projected size selects full detail. A new configuration resets hysteresis.
     * The returned level is desired; ModelLodBinding also resolves availability.
     */
    public int select(ModelLodConfig config, float projectedPixels) {
        if (config == null) throw new IllegalArgumentException("LOD config cannot be null");
        if (this.config != config) { reset(); this.config = config; }
        if (!Float.isFinite(projectedPixels) || projectedPixels < 0) {
            desired = 0;
        } else {
            double down = initialized ? 1.0 - config.hysteresis() : 1.0;
            while (desired + 1 < config.levelCount()
                    && projectedPixels < config.maxScreenPixels(desired + 1) * down) desired++;
            while (desired > 0
                    && projectedPixels > config.maxScreenPixels(desired) * (1.0 + config.hysteresis())) desired--;
        }
        initialized = true;
        rendered = desired;
        fallback = ModelLodFallback.NONE;
        return desired;
    }

    /** Resets transitions and releases the configuration reference. */
    public void reset() {
        config = null; initialized = false; desired = rendered = 0;
        fallback = ModelLodFallback.NONE;
    }
    /** Most recently requested level. */
    public int desiredLevel() { return desired; }
    /** Most recently resolved level (or desired level when using select directly). */
    public int renderedLevel() { return rendered; }
    /** Reason the binding could not use the requested level. */
    public ModelLodFallback fallback() { return fallback; }

    void force(int level) { reset(); desired = rendered = level; }
    void resolved(int level, ModelLodFallback reason) { rendered = level; fallback = reason; }
}
