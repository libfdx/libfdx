package io.github.libfdx.graphics.shader.runtime;

import io.github.libfdx.core.FdxException;
import io.github.libfdx.graphics.GraphicsDevice;

/**
 * Common 2D/3D-independent shader pass provider.
 *
 * <p>The provider resolves exactly one pass requested by a renderer. It does
 * not schedule an entire technique or own a render graph.</p>
 */
public interface ShaderProvider {
    /** Device whose resources preparation creates, or null when async preparation is unsupported. */
    default GraphicsDevice preparationDevice() { return null; }

    /**
     * Starts isolated preparation. Providers advertising runtimeNonblocking must return without
     * waiting or compiling on the caller. Loading-only providers are started exclusively by
     * ShaderPreparation.updateLoading(), which explicitly permits owner-thread compilation.
     * The operation owns retained
     * immutable inputs until completion and transfers result ownership from finish(). Providers
     * must never implement this by calling synchronous resolve() on the application thread.
     */
    default ShaderPreparationOperation beginPreparation(ShaderRequest request) {
        throw new FdxException("Shader provider does not support asynchronous preparation");
    }

    /**
     * Returns whether this provider implements provider-neutral pass
     * resolution.
     *
     * <p>The default is {@code false} so existing domain-specific provider
     * interfaces can extend this common root without acquiring a new required
     * operation.</p>
     *
     * @return whether {@link #resolve(ShaderRequest)} is supported
     */
    default boolean supportsPassResolution() {
        return false;
    }

    /**
     * Returns whether the supplied structural request can be resolved.
     *
     * <p>This query must not create a native pipeline or change the provider
     * revision. Renderers use it during setup to negotiate a stable input
     * ABI.</p>
     *
     * @param request immutable request
     * @return whether the request is supported
     */
    default boolean supports(ShaderRequest request) {
        return false;
    }

    /**
     * Resolves one pass for the supplied structural request.
     *
     * @param request immutable request
     * @return resolved pass
     */
    default ResolvedShaderPass resolve(ShaderRequest request) {
        throw new FdxException(
                "Shader provider does not support provider-neutral pass resolution");
    }

    /**
     * Returns the revision of the atomically visible provider state.
     * A revision fixes source, entry points, binding ABI and fixed pipeline state for an exact
     * structural request. Any change to those inputs must advance the revision. Preparation
     * snapshots that state before worker execution; workers never read mutable provider state.
     *
     * @return non-negative revision
     */
    default long revision() {
        return 0;
    }

    /** Pure revision-transition check, called on the application thread when definitions change.
     * The previous pass was produced by this provider for the same structural request. Return
     * true only when it remains usable with current binding ABI, resource values and pass rules
     * until replacement succeeds. Unknown compatibility skips rendering. Never compile here. */
    default boolean canRenderPreparedRevision(ShaderRequest request, ResolvedShaderPass previous) {
        return previous != null && previous.providerRevision() == revision();
    }

    /** Revision of borrowed default resource values, independent of source/pipeline revision.
     * Uniform, texture and sampler value changes must not cause shader recompilation. */
    default long resourceRevision() { return 0; }

    /** Optional pure recipe contribution. Return stable factory/schema/configuration inputs,
     * or null when the game must supply procedural inputs. Called once per captured structural
     * configuration; must not compile, load assets, inspect mutable frames or perform disk I/O. */
    default ShaderPreloadRecipe preloadRecipe(ShaderRequest request, String targetRole) { return null; }
}
