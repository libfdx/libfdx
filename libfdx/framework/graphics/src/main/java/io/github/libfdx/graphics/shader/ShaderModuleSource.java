package io.github.libfdx.graphics.shader;

import io.github.libfdx.graphics.shader.reflection.ShaderReflection;
import java.util.function.Supplier;
import java.util.Objects;

/** Immutable source-generation input containing no native resources or live frame/material data.
 * Deferred generators must be safe for simultaneous provider workers and may only read captured
 * immutable inputs. Native preparation invokes generate on its advertised CPU execution path.
 * No game callbacks or native device calls belong in a generator. */
public final class ShaderModuleSource {
    private final ShaderModuleDescriptor fixed;
    private final Supplier<ShaderModuleDescriptor> generator;
    private final String vertexEntryPoint, fragmentEntryPoint;

    private ShaderModuleSource(ShaderModuleDescriptor fixed, Supplier<ShaderModuleDescriptor> generator,
            String vertexEntryPoint, String fragmentEntryPoint) {
        this.fixed = fixed; this.generator = generator;
        this.vertexEntryPoint = Objects.requireNonNull(vertexEntryPoint);
        this.fragmentEntryPoint = Objects.requireNonNull(fragmentEntryPoint);
    }
    public static ShaderModuleSource fixed(ShaderModuleDescriptor descriptor) {
        ShaderModuleDescriptor snapshot = Objects.requireNonNull(descriptor).copy();
        return new ShaderModuleSource(snapshot, null, snapshot.vertexEntryPoint(), snapshot.fragmentEntryPoint());
    }
    public static ShaderModuleSource deferred(String vertex, String fragment, Supplier<ShaderModuleDescriptor> generator) {
        return new ShaderModuleSource(null, Objects.requireNonNull(generator), vertex, fragment);
    }
    /** Explicit source-generation operation, potentially expensive. Never call from a ready draw. */
    public ShaderModuleDescriptor generate() {
        return (fixed != null ? fixed : Objects.requireNonNull(generator.get(), "Generated shader source")).copy();
    }
    public String vertexEntryPoint() { return vertexEntryPoint; }
    public String fragmentEntryPoint() { return fragmentEntryPoint; }

    /** Immutable declared metadata, or null for deferred generators. This never invokes source
     * generation. Incomplete metadata cannot establish compatibility across shader revisions. */
    public ShaderReflection declaredReflection() { return fixed != null ? fixed.reflection() : null; }
}
