package io.github.libfdx.graphics.g3d.lod;

/** Immutable, shareable screen-size switching configuration. Level zero is full detail. */
public final class ModelLodConfig {
    /** Maximum number of reduced levels, excluding the base model. */
    public static final int MAX_REDUCED_LEVELS = 8;
    private final float[] thresholds;
    private final float hysteresis;

    /**
     * Copies positive, finite, strictly decreasing pixel thresholds for LOD 1 onward.
     * No thresholds means base only. The default hysteresis is ten percent.
     */
    public ModelLodConfig(float... maxScreenPixels) { this(.1f, maxScreenPixels); }

    private ModelLodConfig(float hysteresis, float[] thresholds) {
        if (thresholds == null || thresholds.length > MAX_REDUCED_LEVELS)
            throw new IllegalArgumentException("LOD requires zero to eight reduced levels");
        if (!Float.isFinite(hysteresis) || hysteresis < 0 || hysteresis >= 1)
            throw new IllegalArgumentException("LOD hysteresis must be finite and in [0, 1)");
        float previous = Float.POSITIVE_INFINITY;
        for (float threshold : thresholds) {
            if (!Float.isFinite(threshold) || threshold <= 0 || threshold >= previous)
                throw new IllegalArgumentException("LOD pixel thresholds must be positive and strictly decreasing");
            previous = threshold;
        }
        this.thresholds = thresholds.clone();
        this.hysteresis = hysteresis;
    }

    /** Returns a new configuration with the same thresholds and the requested hysteresis. */
    public ModelLodConfig withHysteresis(float fraction) { return new ModelLodConfig(fraction, thresholds); }
    /** Total level count, including the mandatory base. */
    public int levelCount() { return thresholds.length + 1; }
    /** Transition threshold for the supplied reduced level (1 through levelCount minus one). */
    public float maxScreenPixels(int level) {
        if (level < 1 || level > thresholds.length) throw new IndexOutOfBoundsException("Reduced LOD level: " + level);
        return thresholds[level - 1];
    }
    /** Fraction around each boundary used to prevent rapid back-and-forth switching. */
    public float hysteresis() { return hysteresis; }
}
