package io.github.libfdx.graphics.shader.runtime;

import io.github.libfdx.core.FdxException;

/**
 * Common 2D/3D-independent shader pass provider.
 *
 * <p>The provider resolves exactly one pass requested by a renderer. It does
 * not schedule an entire technique or own a render graph.</p>
 */
public interface ShaderProvider {
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
     *
     * @return non-negative revision
     */
    default long revision() {
        return 0;
    }
}
