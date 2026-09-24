package io.github.libfdx.graphics.g3d;

import io.github.libfdx.core.FdxException;
import io.github.libfdx.graphics.CompareFunction;
import io.github.libfdx.graphics.GraphicsCapabilities;
import io.github.libfdx.graphics.GraphicsContext;
import io.github.libfdx.graphics.GraphicsFeature;
import io.github.libfdx.graphics.Mesh;
import io.github.libfdx.graphics.shader.runtime.ShaderPassId;
import io.github.libfdx.graphics.shader.runtime.ShaderProvider;
import io.github.libfdx.graphics.shader.ShaderModuleSource;
import io.github.libfdx.graphics.shader.ShaderProfile;
import io.github.libfdx.graphics.shadergraph.compiler.ShaderGraphCompileOptions;
import io.github.libfdx.graphics.shadergraph.compiler.ShaderGraphCompiler;
import io.github.libfdx.graphics.shadergraph.compiler.ShaderGraphCompileResult;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraph;
import io.github.libfdx.graphics.shadergraph.runtime.ShaderGraphMaterialDefinition;
import io.github.libfdx.graphics.shadergraph.runtime.ShaderGraphRenderProgram;
import io.github.libfdx.graphics.shadergraph.runtime.ShaderGraphRenderTechnique;
import io.github.libfdx.graphics.shadergraph.runtime.ShaderGraphRenderTechniquePass;
import io.github.libfdx.graphics.shadergraph.runtime.ShaderGraphRenderVariant;
import io.github.libfdx.graphics.shadergraph.runtime.ShaderGraphRuntimeGraph;
import io.github.libfdx.math.ClipDepthRange;
import io.github.libfdx.core.FdxFuture;
import io.github.libfdx.graphics.shader.internal.ShaderCompilationTasks;
import java.util.function.Consumer;

/**
 * Framework-owned graph-composed PBR technique for ModelBatch's common
 * {@code ShaderProvider} path.
 *
 * <p>The renderer scaffold remains responsible for camera, environment,
 * object, texture, skinning, and draw bindings. The supplied surface graph
 * controls material surface evaluation and can be replaced without editing
 * the scaffold WGSL.</p>
 */
public final class StandardPbrTechnique {
    private static final ShaderPassId[] STANDARD_PASSES = {
            ShaderPassId.FORWARD,
            ShaderPassId.DEPTH,
            ShaderPassId.SHADOW,
            ShaderPassId.PICKING
    };

    private final PbrGraphCustomization customization;
    private final DeferredCustomization deferred;
    private final ShaderGraphRenderTechnique technique;
    private final ShaderGraph surfaceGraph;
    private final ShaderGraph vertexGraph;
    private final ShaderGraph lightingGraph;
    private final boolean alphaBlendControl;

    private StandardPbrTechnique(GraphicsContext graphics,
            ShaderGraph surfaceGraph, ShaderGraph vertexGraph,
            ShaderGraph lightingGraph) {
        this(graphics, surfaceGraph, vertexGraph,
                lightingGraph, null);
    }

    private StandardPbrTechnique(GraphicsContext graphics,
            ShaderGraph surfaceGraph, ShaderGraph vertexGraph,
            ShaderGraph lightingGraph,
            ShaderGraphRuntimeGraph surfaceCompilation) {
        this(graphics, surfaceGraph, vertexGraph, lightingGraph, surfaceCompilation, false);
    }

    private StandardPbrTechnique(GraphicsContext graphics, ShaderGraph surfaceGraph,
            ShaderGraph vertexGraph, ShaderGraph lightingGraph,
            ShaderGraphRuntimeGraph surfaceCompilation, boolean prepareSources) {
        this(graphics, surfaceGraph, vertexGraph, lightingGraph, surfaceCompilation, prepareSources, null);
    }

    private StandardPbrTechnique(GraphicsContext graphics, ShaderGraph surfaceGraph,
            ShaderGraph vertexGraph, ShaderGraph lightingGraph,
            ShaderGraphRuntimeGraph surfaceCompilation, boolean prepareSources, StandardPbrSourcePreparer preparer) {
        if (graphics == null || surfaceGraph == null
                || vertexGraph == null || lightingGraph == null) {
            throw new FdxException(
                    "Standard PBR technique requires graphics, surface, "
                            + "vertex, and lighting graphs");
        }
        this.surfaceGraph = surfaceGraph;
        this.vertexGraph = vertexGraph;
        this.lightingGraph = lightingGraph;
        alphaBlendControl = graphics.device().capabilities().supports(
                GraphicsFeature.ALPHA_BLEND_CONTROL)
                || graphics.device().capabilities().supports(
                        GraphicsFeature.COMPLETE_RENDER_PIPELINE_STATE);
        GraphicsCapabilities capabilities = graphics.device().capabilities();
        deferred = prepareSources ? new DeferredCustomization(capabilities, surfaceGraph, vertexGraph, lightingGraph, preparer) : null;
        customization = prepareSources ? null : compileCustomization(capabilities, surfaceGraph, vertexGraph, lightingGraph, surfaceCompilation);
        ShaderGraphRenderTechniquePass[] passes =
                new ShaderGraphRenderTechniquePass[
                        STANDARD_PASSES.length];
        for (int i = 0; i < passes.length; i++) {
            ShaderPassId passId = STANDARD_PASSES[i];
            ShaderGraphRenderVariant[] variants = new ShaderGraphRenderVariant[24];
            int next = 0;
            for (int bits = 0; bits < 8; bits++) {
                boolean skinned = (bits & 1) != 0, textured = (bits & 2) != 0, colors = (bits & 4) == 0;
                for (MaterialAlphaMode alpha : MaterialAlphaMode.values()) {
                    boolean blend = alpha == MaterialAlphaMode.BLEND;
                    String key = PbrShaderProvider.variantKey(skinned, alpha, textured, colors);
                    variants[next++] = ShaderGraphRenderVariant.builder(key,
                            program(passId, skinned, alpha != MaterialAlphaMode.OPAQUE, blend, !blend,
                                    alpha.name(), textured, colors)).build();
                }
            }
            passes[i] = ShaderGraphRenderTechniquePass.builder(passId).variants(variants).build();
        }
        technique = ShaderGraphRenderTechnique.of(
                "libfdx.standard.pbr", passes);
    }

    /** Creates standard source definitions without compiling graphs, generating WGSL or creating
     * native modules. Source generation runs inside the provider's preparation operation. */
    static ShaderGraphRenderTechnique preparationTechnique(GraphicsContext graphics) {
        return preparationTechnique(graphics, null);
    }
    static ShaderGraphRenderTechnique preparationTechnique(GraphicsContext graphics, StandardPbrSourcePreparer preparer) {
        return new StandardPbrTechnique(graphics, StandardPbrSurfaceGraph.create(), StandardPbrVertexGraph.create(),
                StandardPbrLightingGraph.create(), null, true, preparer).technique();
    }

    private ShaderModuleSource source(boolean skinned, boolean alphaTest, boolean textured, boolean colors) {
        return deferred != null ? deferred.sources[(skinned ? 1 : 0) | (alphaTest ? 2 : 0) | (textured ? 4 : 0) | (colors ? 0 : 8)]
                : ShaderModuleSource.fixed(customization.shader(skinned, alphaTest, textured, colors));
    }

    private static final class DeferredCustomization {
        final GraphicsCapabilities capabilities;
        final ShaderGraph surface, vertex, lighting;
        final ShaderModuleSource[] sources = new ShaderModuleSource[16];
        volatile PbrGraphCustomization ready;
        final StandardPbrSourcePreparer preparer;
        DeferredCustomization(GraphicsCapabilities capabilities, ShaderGraph surface, ShaderGraph vertex, ShaderGraph lighting,
                StandardPbrSourcePreparer preparer) {
            this.capabilities = capabilities; this.surface = surface; this.vertex = vertex; this.lighting = lighting;
            this.preparer = preparer;
            for (int i = 0; i < sources.length; i++) {
                final int variant = i;
                sources[i] = ShaderModuleSource.deferred("vertexMain", "fragmentMain",
                        () -> generate().shader((variant & 1) != 0, (variant & 2) != 0, (variant & 4) != 0, (variant & 8) == 0),
                        execute -> ShaderCompilationTasks.then(generateAsync(execute), execute,
                                value -> FdxFuture.completed(value.shader((variant & 1) != 0, (variant & 2) != 0, (variant & 4) != 0, (variant & 8) == 0))));
            }
        }
        // Only CPU preparation workers call this monitor. Owner-thread default lookup never waits.
        synchronized PbrGraphCustomization generate() {
            if (ready == null) ready = compileCustomization(capabilities, surface, vertex, lighting, null);
            return ready;
        }
        synchronized FdxFuture<PbrGraphCustomization> generateAsync(Consumer<Runnable> execute) {
            if (ready != null) return FdxFuture.completed(ready);
            if (preparer == null) return ShaderCompilationTasks.submit(execute, this::generate);
            // Each caller gets its own restore continuation. Never tie shared work to the first
            // scope's executor: that scope can be paused or cancelled while another remains live.
            ShaderProfile profile = profile(capabilities);
            return ShaderCompilationTasks.then(preparer.prepare(profile, execute), execute, result -> {
                if (result.profile() != profile) throw new FdxException("PBR worker returned a different profile");
                synchronized (this) { if (ready == null) ready = result.restore(); }
                return FdxFuture.completed(ready);
            });
        }
        GraphMaterial defaults() {
            PbrGraphCustomization value = ready;
            return value != null ? value.defaultMaterial() : null;
        }
    }

    private static PbrGraphCustomization compileCustomization(GraphicsCapabilities capabilities,
            ShaderGraph surfaceGraph, ShaderGraph vertexGraph, ShaderGraph lightingGraph,
            ShaderGraphRuntimeGraph surfaceCompilation) {
        return compileCustomization(profile(capabilities), capabilities, surfaceGraph, vertexGraph, lightingGraph, surfaceCompilation);
    }
    private static ShaderProfile profile(GraphicsCapabilities capabilities) {
        return capabilities
                .supports(ShaderProfile.PORTABLE_WEBGPU)
                        ? ShaderProfile.PORTABLE_WEBGPU
                        : capabilities.supports(
                                ShaderProfile.PORTABLE_WEBGL2)
                                        ? ShaderProfile.PORTABLE_WEBGL2
                                        : ShaderProfile.NATIVE;
    }
    static PbrGraphCustomization compileCustomization(ShaderProfile profile, GraphicsCapabilities capabilities,
            ShaderGraph surfaceGraph, ShaderGraph vertexGraph, ShaderGraph lightingGraph,
            ShaderGraphRuntimeGraph surfaceCompilation) {
        ShaderGraphCompiler compiler = new ShaderGraphCompiler();
        ShaderGraphCompileOptions options =
                ShaderGraphCompileOptions.builder()
                        .profile(profile)
                        .capabilities(capabilities)
                        .build();
        ShaderGraphMaterialDefinition definition =
                surfaceCompilation != null
                        ? ShaderGraphMaterialDefinition.compiled(
                                surfaceCompilation)
                        : ShaderGraphMaterialDefinition.compile(
                                surfaceGraph, compiler, options);
        ShaderGraphCompileResult vertexCompilation =
                requireSuccessful("vertex",
                        compiler.compile(vertexGraph, options));
        ShaderGraphCompileResult lightingCompilation =
                requireSuccessful("lighting",
                        compiler.compile(lightingGraph, options));
        return new PbrGraphCustomization(definition,
                profile,
                vertexGraph, vertexCompilation, lightingGraph,
                lightingCompilation);
    }

    private ShaderGraphRenderProgram program(ShaderPassId passId,
            boolean skinned, boolean alphaTest, boolean alphaBlend,
            boolean depthWrite, String alphaLabel, boolean textured, boolean colors) {
        return ShaderGraphRenderProgram.builder(passId,
                        source(skinned, alphaTest, textured, colors))
                .label("standard graph "
                        + (skinned ? "skinned " : "")
                        + alphaLabel + " PBR " + passId)
                // Not LESS_EQUAL: under reversed depth the nearer fragment is
                // the one with the LARGER value, and this technique draws the
                // scene's models - hardcoding the test would blank them.
                .depth(depthWrite, CompareFunction.depthTestFor(
                        ClipDepthRange.getDefault()))
                // Providers without explicit blend-state control retain the
                // historical always-blended pipeline as a safe fallback.
                .alphaBlend(alphaBlend || !alphaBlendControl)
                .vertexLayouts(Mesh.pbrLayout(skinned, textured, colors))
                .preparedDefaults(deferred != null ? deferred::defaults : customization::defaultMaterial)
                .build();
    }

    /**
     * Creates the standard PBR technique and surface graph.
     *
     * @param graphics graphics context
     * @return standard PBR artifact
     */
    public static StandardPbrTechnique create(
            GraphicsContext graphics) {
        return new StandardPbrTechnique(graphics,
                StandardPbrSurfaceGraph.create(),
                StandardPbrVertexGraph.create(),
                StandardPbrLightingGraph.create());
    }

    /**
     * Creates a PBR technique using a replacement surface graph.
     *
     * @param graphics graphics context
     * @param surfaceGraph complete PBR surface graph
     * @return customized PBR artifact
     */
    public static StandardPbrTechnique create(
            GraphicsContext graphics, ShaderGraph surfaceGraph) {
        return new StandardPbrTechnique(graphics,
                surfaceGraph, StandardPbrVertexGraph.create(),
                StandardPbrLightingGraph.create());
    }

    /**
     * Creates a PBR technique from a cache-hit or cache-miss compiled surface
     * graph without lowering or emitting that graph again.
     *
     * @param graphics graphics context
     * @param surfaceCompilation compiled surface graph
     * @return customized PBR artifact
     */
    public static StandardPbrTechnique create(
            GraphicsContext graphics,
            ShaderGraphRuntimeGraph surfaceCompilation) {
        if (surfaceCompilation == null) {
            throw new FdxException(
                    "Standard PBR surface compilation cannot be null");
        }
        return new StandardPbrTechnique(graphics,
                surfaceCompilation.graph(),
                StandardPbrVertexGraph.create(),
                StandardPbrLightingGraph.create(),
                surfaceCompilation);
    }

    /**
     * Starts a fully customizable standard PBR composition.
     *
     * @param graphics graphics context
     * @return technique builder
     */
    public static Builder builder(GraphicsContext graphics) {
        return new Builder(graphics);
    }

    /**
     * Returns the complete pass/variant artifact accepted by
     * {@code ShaderGraphProvider}.
     *
     * @return render technique
     */
    public ShaderGraphRenderTechnique technique() {
        return technique;
    }

    /**
     * Returns a one-pass view of this technique for render paths that install
     * separate providers per pass.
     *
     * @param passId pass semantic
     * @return immutable one-pass technique
     */
    public ShaderGraphRenderTechnique passTechnique(
            ShaderPassId passId) {
        ShaderGraphRenderTechniquePass pass =
                technique.pass(passId);
        if (pass == null) {
            throw new FdxException(
                    "Standard PBR technique has no pass "
                            + passId);
        }
        return ShaderGraphRenderTechnique.of(
                technique.id() + "." + passId.value(), pass);
    }

    /**
     * Creates a material instance for this technique's surface schema.
     *
     * @param id material ID
     * @return graph material
     */
    public GraphMaterial material(String id) {
        return customization.material(id);
    }

    /**
     * Returns the surface material schema.
     *
     * @return material definition
     */
    public ShaderGraphMaterialDefinition materialDefinition() {
        return customization.definition();
    }

    /**
     * Returns the composed surface graph.
     *
     * @return surface graph
     */
    public ShaderGraph surfaceGraph() {
        return surfaceGraph;
    }

    /**
     * Returns the composed local-space vertex extension graph.
     *
     * @return vertex graph
     */
    public ShaderGraph vertexGraph() {
        return vertexGraph;
    }

    /**
     * Returns the composed final-lighting extension graph.
     *
     * @return lighting graph
     */
    public ShaderGraph lightingGraph() {
        return lightingGraph;
    }

    private static ShaderGraphCompileResult requireSuccessful(
            String label, ShaderGraphCompileResult result) {
        if (result != null && result.success()) {
            return result;
        }
        StringBuilder message = new StringBuilder(
                "Could not compile standard PBR ")
                .append(label).append(" extension graph");
        if (result != null) {
            for (var diagnostic : result.diagnostics()) {
                message.append('\n').append(diagnostic.code())
                        .append(": ").append(
                                diagnostic.message());
            }
        }
        throw new FdxException(message.toString());
    }

    /**
     * Mutable setup scope. Built techniques and graphs are immutable.
     */
    public static final class Builder {
        private final GraphicsContext graphics;
        private ShaderGraph surfaceGraph =
                StandardPbrSurfaceGraph.create();
        private ShaderGraph vertexGraph =
                StandardPbrVertexGraph.create();
        private ShaderGraph lightingGraph =
                StandardPbrLightingGraph.create();

        private Builder(GraphicsContext graphics) {
            if (graphics == null) {
                throw new FdxException(
                        "Standard PBR technique requires graphics");
            }
            this.graphics = graphics;
        }

        /**
         * Replaces surface evaluation.
         *
         * @param value surface graph
         * @return this builder
         */
        public Builder surfaceGraph(ShaderGraph value) {
            surfaceGraph = value;
            return this;
        }

        /**
         * Replaces post-skinning local vertex processing.
         *
         * @param value function graph following
         *        {@link StandardPbrVertexGraph}'s contract
         * @return this builder
         */
        public Builder vertexGraph(ShaderGraph value) {
            vertexGraph = value;
            return this;
        }

        /**
         * Replaces final linear lighting.
         *
         * @param value function graph following
         *        {@link StandardPbrLightingGraph}'s contract
         * @return this builder
         */
        public Builder lightingGraph(ShaderGraph value) {
            lightingGraph = value;
            return this;
        }

        /**
         * Builds the immutable technique and all pass variants.
         *
         * @return standard PBR technique
         */
        public StandardPbrTechnique build() {
            return new StandardPbrTechnique(graphics,
                    surfaceGraph, vertexGraph, lightingGraph);
        }
    }
}
