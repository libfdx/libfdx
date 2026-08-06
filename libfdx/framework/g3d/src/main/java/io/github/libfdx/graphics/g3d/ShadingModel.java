package io.github.libfdx.graphics.g3d;

import io.github.libfdx.graphics.internal.ShaderStableId;

/**
 * Stable material-surface semantic used by a {@link ShaderProvider3D} to
 * select shading behavior.
 *
 * <p>A shading model is independent from the render path. For example, a
 * forward or deferred render path may both support {@link #PBR}, while an
 * unlit material can be submitted alongside either one. Applications may
 * create custom IDs, but the selected shader provider must explicitly support
 * them.</p>
 */
public final class ShadingModel implements Comparable<ShadingModel> {
    /** Standard physically based lighting. */
    public static final ShadingModel PBR = of("pbr");
    /** Base color and emissive output without scene-light evaluation. */
    public static final ShadingModel UNLIT = of("unlit");

    private final String id;

    private ShadingModel(String id) {
        this.id = ShaderStableId.normalize(id, "Shading model");
    }

    /**
     * Creates a stable shading-model ID.
     *
     * @param id non-empty identifier
     * @return shading model
     */
    public static ShadingModel of(String id) {
        return new ShadingModel(id);
    }

    /** @return stable identifier */
    public String id() {
        return id;
    }

    @Override
    public int compareTo(ShadingModel other) {
        return other != null ? id.compareTo(other.id) : 1;
    }

    @Override
    public boolean equals(Object object) {
        return object instanceof ShadingModel other
                && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return id;
    }
}
