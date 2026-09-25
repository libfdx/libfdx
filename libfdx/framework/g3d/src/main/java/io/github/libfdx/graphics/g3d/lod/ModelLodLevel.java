package io.github.libfdx.graphics.g3d.lod;

/** One immutable reduced-level asset description; paths are resolved relative to the supplied base asset. */
public record ModelLodLevel(String assetPath, float maxScreenPixels, long triangleCount) {
    /** Rejects absent paths, invalid thresholds and negative counts. Path containment is checked by the definition reader. */
    public ModelLodLevel {
        if (assetPath == null || assetPath.isBlank() || !Float.isFinite(maxScreenPixels)
                || maxScreenPixels <= 0 || triangleCount < 0)
            throw new IllegalArgumentException("Invalid LOD asset, threshold or triangle count");
    }
}
