package io.github.libfdx.graphics.meshoptimizer;

import io.github.libfdx.graphics.g3d.lod.ModelLodConfig;

/** Immutable generation policy. Generation is preparation, never a draw operation. Attribute seams stay protected. */
public record ModelLodSettings(ModelLodTarget[] targets, float hysteresis, boolean lockBorders,
        float normalWeight, float uvWeight, float colorWeight,
        boolean optimizeCache, boolean optimizeFetch) {
    public ModelLodSettings {
        if (targets == null || targets.length < 1 || targets.length > ModelLodConfig.MAX_REDUCED_LEVELS)
            throw new IllegalArgumentException("Configure one to eight reduced LODs");
        targets = targets.clone();
        float previousRatio = 1, previousPixels = Float.POSITIVE_INFINITY;
        for (ModelLodTarget target : targets) {
            if (target == null || target.triangleRatio() >= previousRatio || target.maxScreenPixels() >= previousPixels)
                throw new IllegalArgumentException("Triangle ratios and pixel thresholds must strictly decrease");
            previousRatio = target.triangleRatio(); previousPixels = target.maxScreenPixels();
        }
        if (!Float.isFinite(hysteresis) || hysteresis < 0 || hysteresis >= 1)
            throw new IllegalArgumentException("Hysteresis must be in [0, 1)");
        weight(normalWeight); weight(uvWeight); weight(colorWeight);
    }
    private static void weight(float value) {
        if (!Float.isFinite(value) || value < 0 || value > 1000)
            throw new IllegalArgumentException("Attribute weights must be finite and between zero and 1000");
    }
    @Override public ModelLodTarget[] targets() { return targets.clone(); }
    public int levelCount() { return targets.length; }
    public ModelLodTarget target(int index) { return targets[index]; }
    public ModelLodConfig runtimeConfig() {
        float[] pixels = new float[targets.length];
        for (int i = 0; i < pixels.length; i++) pixels[i] = targets[i].maxScreenPixels();
        return new ModelLodConfig(pixels).withHysteresis(hysteresis);
    }
    public static ModelLodSettings balanced() {
        return new ModelLodSettings(new ModelLodTarget[] {
                new ModelLodTarget(.5f,.01f,240), new ModelLodTarget(.2f,.03f,100),
                new ModelLodTarget(.07f,.08f,40)}, .1f,false,.5f,10,1,true,true);
    }
}
