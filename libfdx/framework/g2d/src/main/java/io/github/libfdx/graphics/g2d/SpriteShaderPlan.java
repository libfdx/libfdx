package io.github.libfdx.graphics.g2d;

import io.github.libfdx.core.FdxException;
import io.github.libfdx.graphics.*;
import io.github.libfdx.graphics.shader.runtime.*;
import io.github.libfdx.graphics.shader.ShaderModuleDescriptor;
import io.github.libfdx.graphics.shader.ShaderProfile;
import java.util.Arrays;
import java.util.function.Function;
import java.util.Map;
import java.util.Objects;

/** Immutable sprite source/ABI selection shared by preload collection and rendering.
 * Creating this plan allocates no native shaders or pipelines. Include every intended target
 * layout in a retained scope, and pass this same plan to SpriteBatchConfig.shaderPlan().
 * The optional custom provider is borrowed and must outlive its preparation requests. */
public final class SpriteShaderPlan {
    private final GraphicsContext graphics;
    final ShaderProvider shaderProvider;
    private final Object domain;
    private final boolean builtins;
    private final ShaderPreloadTargets targets = new ShaderPreloadTargets();

    public SpriteShaderPlan(GraphicsContext graphics) { this(graphics, null); }

    public SpriteShaderPlan(GraphicsContext graphics, ShaderProvider provider) {
        this.graphics = Objects.requireNonNull(graphics, "graphics");
        domain = graphics.device().resourceDomain();
        builtins = provider == null;
        targets.register("surface", RenderTargetLayout.color(graphics.surfaceFormat()));
        shaderProvider = new ValidatedProvider(provider != null ? provider : new Builtins(graphics.device()));
    }

    /** Register logical offscreen roles before capture; the surface role is updated by begin(). */
    public ShaderPreloadTargets targets() { return targets; }

    ShaderPreparationOrigin origin(SpriteShaderAbi abi, ShaderRequest request, RenderTargetLayout target) {
        String role = targets.role(target);
        ShaderPreloadRecipe recipe = builtins && role != null ? new ShaderPreloadRecipe("libfdx.sprite", 1, role,
                Map.of("abi", abi.name()), Map.of()) : role != null ? shaderProvider.preloadRecipe(request, role) : null;
        return new ShaderPreparationOrigin("SpriteBatch", "", "", "", recipe);
    }

    /** Resolves captured built-in requirements against current logical targets and capabilities.
     * A custom provider requires a game-owned factory with its stable source/configuration inputs. */
    public ShaderPreloadResolver.Resolution resolve(ShaderPreloadRecipe recipe, Function<String, RenderTargetLayout> targetResolver) {
        requireDomain(graphics.device());
        if (!builtins || !recipe.factory().equals("libfdx.sprite")) return ShaderPreloadResolver.Resolution.requiresInput("Register factory " + recipe.factory());
        if (recipe.version() != 1 || !recipe.conditions().isEmpty()) return ShaderPreloadResolver.Resolution.stale("Sprite recipe schema/conditions changed");
        RenderTargetLayout target = targetResolver.apply(recipe.targetRole());
        if (target == null) return ShaderPreloadResolver.Resolution.requiresInput("Map target role " + recipe.targetRole());
        SpriteShaderAbi abi = SpriteShaderAbi.valueOf(recipe.parameters().get("abi"));
        SpriteSelection selection = negotiateProvider(RenderPassCompatibility.layout(target));
        if (abi != selection.ordinary && abi != selection.white && abi != selection.packed && abi != selection.compact) {
            return ShaderPreloadResolver.Resolution.unsupported("Captured sprite geometry ABI is not selected on this provider; capture this platform's path");
        }
        return ShaderPreloadResolver.Resolution.resolved(shaderProvider, request(abi, selection.profile, RenderPassCompatibility.layout(target)));
    }

    void requireDomain(GraphicsDevice device) {
        if (domain != device.resourceDomain()) throw new FdxException("Sprite plan belongs to another graphics domain");
    }

    /** Collects the same selected geometry paths used by a batch. No native work is started. */
    public void include(ShaderPreparationScope scope, RenderTargetLayout target) {
        RenderPassCompatibility compatibility = RenderPassCompatibility.layout(target);
        SpriteSelection selection = negotiateProvider(compatibility);
        if (selection.ordinary != null) scope.include(shaderProvider, request(selection.ordinary, selection.profile, compatibility));
        if (selection.white != null) scope.include(shaderProvider, request(selection.white, selection.profile, compatibility));
        if (selection.packed != null) scope.include(shaderProvider, request(selection.packed, selection.profile, compatibility));
        if (selection.compact != null) scope.include(shaderProvider, request(selection.compact, selection.profile, compatibility));
    }

    private static final class Builtins implements ShaderProvider {
        private final GraphicsDevice device;
        Builtins(GraphicsDevice device) { this.device = device; }
        @Override public GraphicsDevice preparationDevice() { return device; }
        @Override public boolean supportsPassResolution() { return true; }
        private SpriteShaderAbi abi(ShaderRequest request) {
            for (SpriteShaderAbi abi : SpriteShaderAbi.values()) {
                if (abi.passId().equals(request.passId())) return abi;
            }
            return null;
        }
        @Override public boolean supports(ShaderRequest request) {
            SpriteShaderAbi abi = abi(request);
            return abi != null && abi != SpriteShaderAbi.PACKED_INSTANCED_INDEXED
                    && request.renderPass() != null && request.variantKey().isEmpty()
                    && request.topology() == PrimitiveTopology.TRIANGLE_LIST
                    && device.capabilities().supports(request.profile())
                    && Arrays.equals(abi.vertexLayouts(), request.vertexLayouts());
        }
        @Override public ShaderPreparationOperation beginPreparation(ShaderRequest request) {
            if (!supports(request)) throw new FdxException("Unsupported built-in sprite request");
            SpriteShaderAbi abi = abi(request);
            String source = switch (abi) {
                case ORDINARY, ORDINARY_INDEXED -> SpriteBatch.SPRITE_SHADER_SOURCE;
                case WHITE, WHITE_INDEXED -> SpriteBatch.WHITE_SPRITE_SHADER_SOURCE;
                case PACKED_INSTANCED, PACKED_INSTANCED_INDEXED -> SpriteBatch.INSTANCED_SPRITE_SHADER_SOURCE;
                case COMPACT_INSTANCED, COMPACT_INSTANCED_INDEXED -> SpriteBatch.COMPACT_INSTANCED_SPRITE_SHADER_SOURCE;
            };
            return device.prepareRenderPipeline(new ShaderPipelineRequest(
                    ShaderModuleDescriptor.wgsl("sprite " + abi, source),
                    new RenderPipelineDescriptor().label("sprite " + abi)
                            .renderTargetLayout(request.renderPass().targetLayout())
                            .primitiveTopology(request.topology()).vertexLayouts(request.vertexLayouts())
                            .vertexEntryPoint("vertexMain").fragmentEntryPoint("fragmentMain")
                            .sampledTextureCount(1), request.passId(), revision()));
        }
    }

    private static final class ValidatedProvider implements ShaderProvider {
        private final ShaderProvider delegate;
        ValidatedProvider(ShaderProvider delegate) { this.delegate = delegate; }
        @Override public GraphicsDevice preparationDevice() { return delegate.preparationDevice(); }
        @Override public boolean supportsPassResolution() { return delegate.supportsPassResolution(); }
        @Override public boolean supports(ShaderRequest request) { return delegate.supports(request); }
        @Override public long revision() { return delegate.revision(); }
        @Override public boolean canRenderPreparedRevision(ShaderRequest request, ResolvedShaderPass previous) {
            return delegate.canRenderPreparedRevision(request, previous);
        }
        @Override public ShaderPreloadRecipe preloadRecipe(ShaderRequest request, String targetRole) { return delegate.preloadRecipe(request, targetRole); }
        @Override public ResolvedShaderPass resolve(ShaderRequest request) { return validate(request, delegate.resolve(request)); }
        @Override public ShaderPreparationOperation beginPreparation(ShaderRequest request) {
            ShaderPreparationOperation operation = delegate.beginPreparation(request);
            return new ShaderPreparationOperation() {
                @Override public boolean isDone() { return operation.isDone(); }
                @Override public ShaderPreparationPhase phase() { return operation.phase(); }
            @Override public ShaderPreparationTrace trace() { return operation.trace(); }
                @Override public void cancel() { operation.cancel(); }
                @Override public boolean isDisposed() { return operation.isDisposed(); }
                @Override public void dispose() { operation.dispose(); }
                @Override public ShaderPreparedResult finish() {
                    ShaderPreparedResult result = operation.finish();
                    try { validate(request, result.pass()); return result; }
                    catch (Throwable failure) { result.dispose(); throw failure; }
                }
            };
        }
        private static ResolvedShaderPass validate(ShaderRequest request, ResolvedShaderPass result) {
            for (SpriteShaderAbi abi : SpriteShaderAbi.values()) {
                if (abi.passId().equals(request.passId())) return SpriteBatch.validateSpritePass(abi, result);
            }
            throw new FdxException("Unknown sprite pass: " + request.passId());
        }
    }
    SpriteSelection negotiateProvider(
            RenderPassCompatibility compatibility) {
        ShaderProfile[] profiles = {
                ShaderProfile.PORTABLE_WEBGPU,
                ShaderProfile.PORTABLE_WEBGL2,
                ShaderProfile.NATIVE
        };
        boolean indexedDraw = graphics.device().capabilities()
                .supports(GraphicsFeature.INDEXED_DRAW);
        boolean instancedDraw = graphics.device().capabilities()
                .supports(GraphicsFeature.INSTANCED_DRAW);
        for (ShaderProfile profile : profiles) {
            if (!graphics.device().capabilities().supports(profile)) {
                continue;
            }
            if (instancedDraw && indexedDraw
                    && supportsAbi(SpriteShaderAbi.PACKED_INSTANCED_INDEXED,
                            profile, compatibility)
                    && supportsAbi(SpriteShaderAbi.COMPACT_INSTANCED_INDEXED,
                            profile, compatibility)) {
                return new SpriteSelection(profile, null, null,
                        SpriteShaderAbi.PACKED_INSTANCED_INDEXED,
                        SpriteShaderAbi.COMPACT_INSTANCED_INDEXED);
            }
            if (instancedDraw
                    && supportsAbi(SpriteShaderAbi.PACKED_INSTANCED,
                            profile, compatibility)
                    && supportsAbi(SpriteShaderAbi.COMPACT_INSTANCED,
                            profile, compatibility)) {
                return new SpriteSelection(profile, null, null,
                        SpriteShaderAbi.PACKED_INSTANCED,
                        SpriteShaderAbi.COMPACT_INSTANCED);
            }
            if (indexedDraw
                    && supportsAbi(SpriteShaderAbi.ORDINARY_INDEXED,
                            profile, compatibility)) {
                SpriteShaderAbi white = supportsAbi(
                        SpriteShaderAbi.WHITE_INDEXED, profile,
                        compatibility)
                        ? SpriteShaderAbi.WHITE_INDEXED : null;
                return new SpriteSelection(profile,
                        SpriteShaderAbi.ORDINARY_INDEXED, white,
                        null, null);
            }
            if (supportsAbi(SpriteShaderAbi.ORDINARY, profile,
                    compatibility)) {
                SpriteShaderAbi white = supportsAbi(
                        SpriteShaderAbi.WHITE, profile, compatibility)
                        ? SpriteShaderAbi.WHITE : null;
                return new SpriteSelection(profile,
                        SpriteShaderAbi.ORDINARY, white, null, null);
            }
        }
        throw new FdxException(
                "SpriteBatch shader provider supports no compatible sprite geometry ABI");
    }

    private boolean supportsAbi(SpriteShaderAbi abi,
            ShaderProfile profile,
            RenderPassCompatibility compatibility) {
        return shaderProvider.supports(request(abi, profile,
                compatibility));
    }

    static ShaderRequest request(SpriteShaderAbi abi,
            ShaderProfile profile,
            RenderPassCompatibility compatibility) {
        return ShaderRequest.builder(abi.passId())
                .profile(profile)
                .renderPass(compatibility)
                .topology(PrimitiveTopology.TRIANGLE_LIST)
                .vertexLayouts(abi.vertexLayouts())
                .build();
    }

    static final class SpriteSelection {
        final ShaderProfile profile;
        final SpriteShaderAbi ordinary;
        final SpriteShaderAbi white;
        final SpriteShaderAbi packed;
        final SpriteShaderAbi compact;

        SpriteSelection(ShaderProfile profile,
                SpriteShaderAbi ordinary, SpriteShaderAbi white,
                SpriteShaderAbi packed, SpriteShaderAbi compact) {
            this.profile = profile;
            this.ordinary = ordinary;
            this.white = white;
            this.packed = packed;
            this.compact = compact;
        }
    }

}
