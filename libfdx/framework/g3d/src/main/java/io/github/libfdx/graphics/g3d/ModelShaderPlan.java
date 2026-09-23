package io.github.libfdx.graphics.g3d;

import io.github.libfdx.core.Disposable;
import io.github.libfdx.core.FdxException;
import io.github.libfdx.graphics.camera.Camera;
import io.github.libfdx.graphics.GraphicsContext;
import io.github.libfdx.graphics.GraphicsDevice;
import io.github.libfdx.graphics.GraphicsFrame;
import io.github.libfdx.graphics.Mesh;
import io.github.libfdx.graphics.PrimitiveTopology;
import io.github.libfdx.graphics.RenderPassCompatibility;
import io.github.libfdx.graphics.RenderPassDescriptor;
import io.github.libfdx.graphics.RenderPipelineDescriptor;
import io.github.libfdx.graphics.RenderTargetLayout;
import io.github.libfdx.graphics.shader.runtime.PreparedShaderPass;
import io.github.libfdx.graphics.shader.runtime.ShaderPassId;
import io.github.libfdx.graphics.shader.runtime.ShaderPipelineRequest;
import io.github.libfdx.graphics.shader.runtime.ShaderPreloadRecipe;
import io.github.libfdx.graphics.shader.runtime.ShaderPreloadResolver;
import io.github.libfdx.graphics.shader.runtime.ShaderPreloadTargets;
import io.github.libfdx.graphics.shader.runtime.ShaderPreloadVertexLayouts;
import io.github.libfdx.graphics.shader.runtime.ShaderPreparationOperation;
import io.github.libfdx.graphics.shader.runtime.ShaderPreparationOrigin;
import io.github.libfdx.graphics.shader.runtime.ShaderPreparationScope;
import io.github.libfdx.graphics.shader.runtime.ShaderProvider;
import io.github.libfdx.graphics.shader.runtime.ShaderRequest;
import io.github.libfdx.graphics.shader.ShaderModuleDescriptor;
import io.github.libfdx.graphics.shader.ShaderProfile;
import io.github.libfdx.graphics.shadergraph.runtime.ShaderGraphProvider;
import io.github.libfdx.graphics.VertexLayout;
import java.util.function.Function;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

/** Application-owned model shader definitions shared by collection and rendering. Native
 * resources belong to preparation entries. Pass the same plan to the loading scope and batches;
 * dispose it after those consumers and their in-flight preparation have drained. Custom common
 * providers are borrowed. Collection reads current mesh/material structure, never native shaders. */
public final class ModelShaderPlan implements Disposable {
    private final GraphicsContext graphics;
    private final Object domain;
    private final ShaderProvider common;
    private final ShaderProvider unavailable;
    private final PositionColorProvider positionColor;
    private final Disposable owned;
    private final StandardPbrSourcePreparer.Owned ownedSources;
    private final ShaderProfile profile;
    private final boolean builtins;
    private final ShaderPreloadTargets targets = new ShaderPreloadTargets();
    private boolean disposed;

    /** Uses the platform's default source strategy (a plan-owned worker on web). */
    public ModelShaderPlan(GraphicsContext graphics) { this(graphics, null); }

    /** Custom providers are borrowed; built-in PBR uses the platform's default source strategy. */
    public ModelShaderPlan(GraphicsContext graphics, ShaderProvider commonProvider) {
        this(graphics, commonProvider, null, ModelShaderPlan::defaultSources);
    }

    /** Borrows an optional preparer for the built-in standard PBR recipe. Custom providers keep
     * their own source strategy. Dispose the preparer after this plan and its preparation scopes.
     * This explicit overload overrides platform defaults; null uses the loading executor. */
    public ModelShaderPlan(GraphicsContext graphics, ShaderProvider commonProvider, StandardPbrSourcePreparer preparer) {
        this(graphics, commonProvider, preparer, null);
    }

    ModelShaderPlan(GraphicsContext graphics, ShaderProvider commonProvider,
            StandardPbrSourcePreparer preparer, Supplier<StandardPbrSourcePreparer.Owned> sourceFactory) {
        this.graphics = Objects.requireNonNull(graphics, "graphics");
        unavailable = new ShaderProvider() {
            @Override
            public GraphicsDevice preparationDevice() { return graphics.device(); }
        };
        domain = graphics.device().resourceDomain();
        profile = profile(graphics.device());
        builtins = commonProvider == null;
        if (commonProvider == null && PbrShaderProvider.usesGpuPbrShader(graphics.providerId().value())) {
            ownedSources = sourceFactory != null ? sourceFactory.get() : null;
            ShaderGraphProvider provider;
            try {
                provider = new ShaderGraphProvider(graphics, StandardPbrTechnique.preparationTechnique(graphics,
                        ownedSources != null ? ownedSources : preparer));
            } catch (RuntimeException | Error failure) {
                if (ownedSources != null) {
                    try { ownedSources.dispose(); } catch (RuntimeException | Error cleanup) { failure.addSuppressed(cleanup); }
                }
                throw failure;
            }
            common = provider;
            owned = provider;
        } else {
            ownedSources = null;
            common = commonProvider;
            owned = null;
        }
        positionColor = commonProvider == null ? new PositionColorProvider(graphics.device()) : null;
    }

    /** Web compilation binds this hook directly to a new plan-owned source worker. */
    private static StandardPbrSourcePreparer.Owned defaultSources() { return null; }

    /** Logical target roles must be registered before capture. Normal frame begin updates surface. */
    public ShaderPreloadTargets targets() { return targets; }

    /** Registers and returns the layout used by {@link ModelBatch#begin(Camera)} on this
     * frame. Call during loading before collecting surface requirements. The model pass
     * enables depth, so the frame's color-only compatibility is not its pipeline layout.
     * This only derives attachment metadata; it does not begin a pass or compile shaders. */
    public RenderTargetLayout surfaceTarget(GraphicsFrame frame) {
        requireDomain(graphics.device());
        RenderTargetLayout target = new RenderPassDescriptor().colorAttachment(frame.colorAttachment())
                .depthEnabled(true).compatibility().targetLayout();
        targets.register("surface", target);
        return target;
    }

    ShaderPreparationOrigin origin(Renderable3D renderable, ShaderRequest request) {
        String role = targets.role(request.renderPass().targetLayout());
        ShaderProvider provider = provider(renderable);
        ShaderPreloadRecipe recipe = role == null || provider == unavailable ? null : builtins
                ? new ShaderPreloadRecipe("libfdx.model", 1, role, Map.of(
                        "path", provider == positionColor ? "position-color" : "pbr",
                        "pass", request.passId().value(), "variant", request.variantKey(),
                        "topology", request.topology().name(), "vertexLayouts", ShaderPreloadVertexLayouts.encode(request.vertexLayouts())), Map.of())
                : provider != null ? provider.preloadRecipe(request, role) : null;
        return new ShaderPreparationOrigin("ModelBatch", renderable.meshPart().mesh().id(), renderable.material().id(), "", recipe);
    }

    /** Imports built-in structural recipes against the current provider and game-owned target roles.
     * Custom recipes are resolved by their registered game factory against current loaded inputs. */
    public ShaderPreloadResolver.Resolution resolve(ShaderPreloadRecipe recipe, Function<String, RenderTargetLayout> targetResolver) {
        requireDomain(graphics.device());
        if (!builtins || !recipe.factory().equals("libfdx.model")) return ShaderPreloadResolver.Resolution.requiresInput("Register factory " + recipe.factory());
        if (recipe.version() != 1 || !recipe.conditions().isEmpty()) return ShaderPreloadResolver.Resolution.stale("Model recipe schema/conditions changed");
        RenderTargetLayout target = targetResolver.apply(recipe.targetRole());
        if (target == null) return ShaderPreloadResolver.Resolution.requiresInput("Map target role " + recipe.targetRole());
        String path = recipe.parameters().get("path");
        ShaderProvider provider = "position-color".equals(path) ? positionColor : "pbr".equals(path) ? common : null;
        if (provider == null) return ShaderPreloadResolver.Resolution.unsupported("Captured model shader path is unavailable on this provider");
        ShaderRequest request = ShaderRequest.builder(ShaderPassId.of(recipe.parameters().get("pass"))).profile(profile)
                .renderPass(RenderPassCompatibility.layout(target)).variantKey(recipe.parameters().get("variant"))
                .topology(PrimitiveTopology.valueOf(recipe.parameters().get("topology")))
                .vertexLayouts(ShaderPreloadVertexLayouts.decode(recipe.parameters().get("vertexLayouts"))).build();
        return provider.supports(request) ? ShaderPreloadResolver.Resolution.resolved(provider, request)
                : ShaderPreloadResolver.Resolution.unsupported("Model shader request is incompatible with current capabilities or source inputs");
    }

    void requireDomain(GraphicsDevice device) {
        if (disposed || domain != device.resourceDomain()) throw new FdxException("Model shader plan is disposed or belongs to another domain");
    }

    ShaderProvider commonProvider() { return common; }
    ShaderProvider unavailableProvider() { return unavailable; }
    boolean supportsPositionColor() { return positionColor != null; }

    static ShaderProfile profile(GraphicsDevice device) {
        return device.capabilities().supports(ShaderProfile.PORTABLE_WEBGPU) ? ShaderProfile.PORTABLE_WEBGPU
                : device.capabilities().supports(ShaderProfile.PORTABLE_WEBGL2) ? ShaderProfile.PORTABLE_WEBGL2 : ShaderProfile.NATIVE;
    }

    ShaderProvider provider(Renderable3D renderable) {
        ShaderProvider3D renderer = renderable.material().shaderProvider();
        if (renderer != null && (!(renderer instanceof PreparedShaderProvider3D prepared)
                || prepared.preparationPlan() != this)) return unavailable;
        Mesh mesh = renderable.meshPart().mesh();
        return common != null && (mesh.vertexLayout() == Mesh.PBR_LAYOUT || mesh.hasPbrSkinning() || mesh.hasPbrTextureCoordinates())
                ? common : positionColor != null ? positionColor : common;
    }

    VertexLayout layout(Renderable3D renderable) {
        Mesh mesh = renderable.meshPart().mesh();
        return provider(renderable) == positionColor && mesh.hasPositionColor3DSource() ? Mesh.POSITION_COLOR_LAYOUT : mesh.vertexLayout();
    }

    String variant(Renderable3D renderable) {
        if (provider(renderable) == positionColor) return "";
        Mesh mesh = renderable.meshPart().mesh();
        return PbrShaderProvider.variantKey(mesh.hasPbrSkinning(), renderable.material().alphaMode(),
                mesh.vertexLayout() == Mesh.PBR_TEXTURED_LAYOUT || mesh.vertexLayout() == Mesh.PBR_TEXTURED_SKINNED_LAYOUT);
    }

    ShaderRequest request(Renderable3D renderable, ShaderPassId pass, RenderTargetLayout target) {
        return ShaderRequest.builder(pass).profile(profile).renderPass(RenderPassCompatibility.layout(target))
                .topology(renderable.meshPart().primitiveTopology()).vertexLayouts(layout(renderable))
                .variantKey(variant(renderable)).build();
    }

    /** Includes this renderable's exact current structural requirement. Returned handle is
     * borrowed from the scope. Declare each intended alpha/quality/layout transition separately.
     * A material renderer that cannot consume this plan yields an UNSUPPORTED handle, without
     * invoking its shader callback. Supply matching PreparedShaderProvider3D definitions to preload it. */
    public PreparedShaderPass include(ShaderPreparationScope scope, Renderable3D renderable,
            ShaderPassId pass, RenderTargetLayout target) {
        requireDomain(graphics.device());
        return scope.include(Objects.requireNonNull(provider(renderable), "No compatible model shader provider"),
                request(renderable, pass, target));
    }

    /** Collects loaded model content with the same selector used by ModelBatch. */
    public void include(ShaderPreparationScope scope, ModelInstance instance, ShaderPassId pass, RenderTargetLayout target) {
        DefaultRenderQueue3D collection = new DefaultRenderQueue3D();
        instance.collectRenderables(collection);
        for (int i = 0; i < collection.size(); i++) include(scope, collection.get(i), pass, target);
    }

    @Override
    public void dispose() {
        if (disposed) return;
        disposed = true;
        try { if (owned != null) owned.dispose(); }
        finally { if (ownedSources != null) ownedSources.dispose(); }
    }
    @Override
    public boolean isDisposed() { return disposed; }

    private static final class PositionColorProvider implements ShaderProvider {
        private final GraphicsDevice device;
        PositionColorProvider(GraphicsDevice device) { this.device = device; }
        @Override
        public GraphicsDevice preparationDevice() { return device; }
        @Override
        public boolean supports(ShaderRequest request) {
            return request.renderPass() != null && request.variantKey().isEmpty()
                    && request.vertexLayouts().length == 1
                    && PbrShaderProvider.isPositionColorLayout(request.vertexLayouts()[0]);
        }
        @Override
        public ShaderPreparationOperation beginPreparation(ShaderRequest request) {
            RenderTargetLayout target = request.renderPass().targetLayout();
            return device.prepareRenderPipeline(new ShaderPipelineRequest(
                    ShaderModuleDescriptor.wgsl("model batch position color", PbrShaderProvider.POSITION_COLOR_SHADER_SOURCE),
                    new RenderPipelineDescriptor().label("model batch position color")
                            .renderTargetLayout(target).primitiveTopology(request.topology())
                            .depthTestEnabled(target.hasDepthStencil()).depthWriteEnabled(target.hasDepthStencil())
                            .vertexLayouts(request.vertexLayouts()), request.passId(), revision()));
        }
    }
}
