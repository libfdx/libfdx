package io.github.libfdx.graphics.meshoptimizer;

/** Bounded deformation sampling. Each clip includes evenly spaced endpoint samples; morph basis
 * poses use the configured range. Procedural poses or values outside this coverage are not certified.
 * Increase the cap deliberately for many clips/targets; exceeding it fails rather than skipping clips. */
public record ModelLodAnimationSettings(int samplesPerClip, int maxPoseSamples,
        float minimumMorphWeight, float maximumMorphWeight) {
    public ModelLodAnimationSettings {
        if (samplesPerClip < 2 || samplesPerClip > 128 || maxPoseSamples < 1 || maxPoseSamples > 256
                || !Float.isFinite(minimumMorphWeight) || !Float.isFinite(maximumMorphWeight)
                || minimumMorphWeight > maximumMorphWeight)
            throw new IllegalArgumentException("Invalid LOD animation sampling policy");
    }
    public static ModelLodAnimationSettings balanced() { return new ModelLodAnimationSettings(5,32,-1,1); }
}
