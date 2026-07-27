package io.github.libfdx.graphics.shader.runtime;

import io.github.libfdx.graphics.RenderPipeline;
import io.github.libfdx.graphics.shader.reflection.ShaderResourceLayout;
import io.github.libfdx.core.FdxException;

/**
 * One resolved pipeline and resource interface returned by a
 * {@link ShaderProvider}.
 */
public final class ResolvedShaderPass {
    private final ShaderPassId passId;
    private final RenderPipeline pipeline;
    private final ShaderResourceLayout resourceLayout;
    private final ShaderResourceBinding defaultResources;
    private final long providerRevision;

    private ResolvedShaderPass(ShaderPassId passId, RenderPipeline pipeline,
            ShaderResourceLayout resourceLayout,
            ShaderResourceBinding defaultResources,
            long providerRevision) {
        if (passId == null || pipeline == null || resourceLayout == null) {
            throw new FdxException("A resolved shader pass requires an ID, pipeline, and resource layout");
        }
        if (providerRevision < 0) {
            throw new FdxException("Shader provider revision cannot be negative");
        }
        this.passId = passId;
        this.pipeline = pipeline;
        this.resourceLayout = resourceLayout;
        this.defaultResources = defaultResources;
        this.providerRevision = providerRevision;
    }

    public static ResolvedShaderPass of(ShaderPassId passId, RenderPipeline pipeline,
            ShaderResourceLayout resourceLayout, long providerRevision) {
        return new ResolvedShaderPass(passId, pipeline, resourceLayout, null,
                providerRevision);
    }

    /**
     * Creates a resolved pass with optional provider-owned default resources.
     *
     * @param passId resolved pass semantic
     * @param pipeline resolved native pipeline
     * @param resourceLayout complete resource layout
     * @param defaultResources borrowed default resources, or {@code null}
     * @param providerRevision provider revision used for resolution
     * @return resolved pass
     */
    public static ResolvedShaderPass of(ShaderPassId passId,
            RenderPipeline pipeline,
            ShaderResourceLayout resourceLayout,
            ShaderResourceBinding defaultResources,
            long providerRevision) {
        return new ResolvedShaderPass(passId, pipeline, resourceLayout,
                defaultResources, providerRevision);
    }

    public ShaderPassId passId() {
        return passId;
    }

    public RenderPipeline pipeline() {
        return pipeline;
    }

    public ShaderResourceLayout resourceLayout() {
        return resourceLayout;
    }

    /**
     * Returns optional provider-owned default resources. A renderer uses these
     * only when its draw/material does not supply an explicit binding.
     *
     * @return borrowed default binding, or {@code null}
     */
    public ShaderResourceBinding defaultResources() {
        return defaultResources;
    }

    public long providerRevision() {
        return providerRevision;
    }
}
