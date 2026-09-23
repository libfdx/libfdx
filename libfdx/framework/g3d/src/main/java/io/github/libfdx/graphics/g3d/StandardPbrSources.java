package io.github.libfdx.graphics.g3d;

import io.github.libfdx.graphics.shader.ShaderProfile;
import io.github.libfdx.graphics.shadergraph.runtime.ShaderGraphMaterialDefinition;
import java.util.Objects;

/** Immutable result of the version-1 standard PBR graph recipe. Variant bits are skinned (1),
 * alpha test (2), and extra texture coordinates (4). No GPU objects or mutable materials are
 * transferred. This is an in-process/worker protocol for matching library versions, not an asset
 * file format or a verifier for externally supplied shader code. */
public final class StandardPbrSources {
    public static final int VERSION = 1;
    private final ShaderProfile profile;
    private final String surface, library;
    private final String[] variants;

    public StandardPbrSources(ShaderProfile profile, String surface, String library, String[] variants) {
        this.profile = Objects.requireNonNull(profile);
        this.surface = requireSource(surface); this.library = requireSource(library);
        if (variants == null || variants.length != 8) throw new IllegalArgumentException("Eight PBR variants required");
        this.variants = variants.clone();
        for (String source : this.variants) requireSource(source);
    }
    private static String requireSource(String source) {
        if (source == null || source.isEmpty()) throw new IllegalArgumentException("PBR source is empty");
        return source;
    }
    /** Runs the canonical CPU compiler/composer. Explicit work; do not call from a ready draw. */
    public static StandardPbrSources compile(ShaderProfile profile) {
        PbrGraphCustomization compiled = StandardPbrTechnique.compileCustomization(profile, null,
                StandardPbrSurfaceGraph.create(), StandardPbrVertexGraph.create(), StandardPbrLightingGraph.create(), null);
        String[] variants = new String[8];
        for (int i = 0; i < 8; i++) variants[i] = compiled.shader((i & 1) != 0, (i & 2) != 0, (i & 4) != 0).wgslSource();
        var graph = compiled.definition().compilation();
        return new StandardPbrSources(profile, graph.wgsl(), graph.libraryWgsl(), variants);
    }
    public ShaderProfile profile() { return profile; }
    public String surface() { return surface; }
    public String library() { return library; }
    public String variant(int index) { return variants[index]; }
    PbrGraphCustomization restore() {
        var definition = ShaderGraphMaterialDefinition.compiled(StandardPbrSurfaceGraph.create(), surface, library);
        return new PbrGraphCustomization(definition, this);
    }
}
