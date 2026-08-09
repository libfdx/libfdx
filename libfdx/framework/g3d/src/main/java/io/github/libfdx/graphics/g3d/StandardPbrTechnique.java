package io.github.libfdx.graphics.g3d;

import io.github.libfdx.graphics.shader.runtime.ShaderProvider;
import io.github.libfdx.core.FdxException;
import io.github.libfdx.graphics.CompareFunction;
import io.github.libfdx.graphics.GraphicsContext;
import io.github.libfdx.graphics.GraphicsFeature;
import io.github.libfdx.graphics.Mesh;
import io.github.libfdx.graphics.shader.runtime.ShaderPassId;
import io.github.libfdx.graphics.shader.ShaderProfile;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraph;
import io.github.libfdx.graphics.shadergraph.compiler.ShaderGraphCompileOptions;
import io.github.libfdx.graphics.shadergraph.compiler.ShaderGraphCompileResult;
import io.github.libfdx.graphics.shadergraph.compiler.ShaderGraphCompiler;
import io.github.libfdx.graphics.shadergraph.runtime.ShaderGraphMaterialDefinition;
import io.github.libfdx.graphics.shadergraph.runtime.ShaderGraphRuntimeGraph;
import io.github.libfdx.graphics.shadergraph.runtime.ShaderGraphRenderProgram;
import io.github.libfdx.graphics.shadergraph.runtime.ShaderGraphRenderTechnique;
import io.github.libfdx.graphics.shadergraph.runtime.ShaderGraphRenderTechniquePass;
import io.github.libfdx.graphics.shadergraph.runtime.ShaderGraphRenderVariant;

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
        ShaderProfile profile = graphics.device().capabilities()
                .supports(ShaderProfile.PORTABLE_WEBGPU)
                        ? ShaderProfile.PORTABLE_WEBGPU
                        : graphics.device().capabilities().supports(
                                ShaderProfile.PORTABLE_WEBGL2)
                                        ? ShaderProfile.PORTABLE_WEBGL2
                                        : ShaderProfile.NATIVE;
        ShaderGraphCompiler compiler = new ShaderGraphCompiler();
        ShaderGraphCompileOptions options =
                ShaderGraphCompileOptions.builder()
                        .profile(profile)
                        .capabilities(graphics.device()
                                .capabilities())
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
        customization = new PbrGraphCustomization(definition,
                profile,
                vertexGraph, vertexCompilation, lightingGraph,
                lightingCompilation);
        ShaderGraphRenderTechniquePass[] passes =
                new ShaderGraphRenderTechniquePass[
                        STANDARD_PASSES.length];
        for (int i = 0; i < passes.length; i++) {
            ShaderPassId passId = STANDARD_PASSES[i];
            ShaderGraphRenderProgram staticOpaque = program(
                    passId, false, false, false, true, "opaque");
            ShaderGraphRenderProgram skinnedOpaque = program(
                    passId, true, false, false, true, "opaque");
            ShaderGraphRenderProgram staticMask = program(
                    passId, false, true, false, true, "mask");
            ShaderGraphRenderProgram skinnedMask = program(
                    passId, true, true, false, true, "mask");
            ShaderGraphRenderProgram staticBlend = program(
                    passId, false, true, true, false, "blend");
            ShaderGraphRenderProgram skinnedBlend = program(
                    passId, true, true, true, false, "blend");
            passes[i] = ShaderGraphRenderTechniquePass
                    .builder(passId)
                    .variants(
                            ShaderGraphRenderVariant.builder("",
                                    staticOpaque).build(),
                            ShaderGraphRenderVariant.builder(
                                    "skinned",
                                    skinnedOpaque).build(),
                            ShaderGraphRenderVariant.builder(
                                    "mask", staticMask).build(),
                            ShaderGraphRenderVariant.builder(
                                    "skinned-mask", skinnedMask).build(),
                            ShaderGraphRenderVariant.builder(
                                    "blend", staticBlend).build(),
                            ShaderGraphRenderVariant.builder(
                                    "skinned-blend", skinnedBlend).build())
                    .build();
        }
        technique = ShaderGraphRenderTechnique.of(
                "libfdx.standard.pbr", passes);
    }

    private ShaderGraphRenderProgram program(ShaderPassId passId,
            boolean skinned, boolean alphaTest, boolean alphaBlend,
            boolean depthWrite, String alphaLabel) {
        return ShaderGraphRenderProgram.builder(passId,
                        customization.shader(skinned, alphaTest))
                .label("standard graph "
                        + (skinned ? "skinned " : "")
                        + alphaLabel + " PBR " + passId)
                .depth(depthWrite, CompareFunction.LESS_EQUAL)
                // Providers without explicit blend-state control retain the
                // historical always-blended pipeline as a safe fallback.
                .alphaBlend(alphaBlend || !alphaBlendControl)
                .vertexLayouts(skinned
                        ? Mesh.PBR_SKINNED_LAYOUT : Mesh.PBR_LAYOUT)
                .defaultResources(customization.defaultMaterial())
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
