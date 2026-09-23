package io.github.libfdx.graphics.shader;

import io.github.libfdx.graphics.shader.reflection.ShaderReflection;
import java.util.function.Supplier;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import io.github.libfdx.core.FdxFuture;
import io.github.libfdx.graphics.shader.internal.ShaderCompilationTasks;

/** Immutable source-generation input containing no native resources or live frame/material data.
 * Deferred generators must be safe for simultaneous provider workers and may only read captured
 * immutable inputs. Native preparation invokes generate on its advertised CPU execution path.
 * No game callbacks or native device calls belong in a generator. */
public final class ShaderModuleSource {
    private final ShaderModuleDescriptor fixed;
    private final Supplier<ShaderModuleDescriptor> generator;
    private final Function<Consumer<Runnable>, FdxFuture<ShaderModuleDescriptor>> asynchronous;
    private final String vertexEntryPoint, fragmentEntryPoint;

    private ShaderModuleSource(ShaderModuleDescriptor fixed, Supplier<ShaderModuleDescriptor> generator,
            String vertexEntryPoint, String fragmentEntryPoint) {
        this(fixed, generator, vertexEntryPoint, fragmentEntryPoint, null);
    }
    private ShaderModuleSource(ShaderModuleDescriptor fixed, Supplier<ShaderModuleDescriptor> generator,
            String vertexEntryPoint, String fragmentEntryPoint,
            Function<Consumer<Runnable>, FdxFuture<ShaderModuleDescriptor>> asynchronous) {
        this.fixed = fixed; this.generator = generator;
        this.asynchronous = asynchronous;
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
    /** Optional borrowed async strategy for transferable CPU source recipes. It must return the
     * same source/metadata as generator, own no GPU resources, and marshal fallback onto execute.
     * Dispose the strategy only after its consumers. Completion may arrive on a platform event
     * loop; consumers must queue subsequent device work on their preparation executor. */
    public static ShaderModuleSource deferred(String vertex, String fragment, Supplier<ShaderModuleDescriptor> generator,
            Function<Consumer<Runnable>, FdxFuture<ShaderModuleDescriptor>> asynchronous) {
        return new ShaderModuleSource(null, Objects.requireNonNull(generator), vertex, fragment,
                Objects.requireNonNull(asynchronous));
    }
    /** Invoked during explicit source preparation; never blocks waiting for asynchronous work. */
    public FdxFuture<ShaderModuleDescriptor> generateAsync(Consumer<Runnable> execute) {
        if (asynchronous == null) return ShaderCompilationTasks.submit(execute, this::generate);
        try {
            return ShaderCompilationTasks.then(Objects.requireNonNull(asynchronous.apply(execute)), execute,
                    value -> FdxFuture.completed(Objects.requireNonNull(value, "Generated shader source").copy()));
        } catch (RuntimeException | Error failure) { return FdxFuture.failed(failure); }
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
