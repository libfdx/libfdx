package io.github.libfdx.graphics.wgpu;

import io.github.libfdx.core.FdxException;
import io.github.libfdx.core.ProviderId;
import io.github.libfdx.graphics.Sampler;
import io.github.libfdx.graphics.shader.reflection.ShaderSamplerKind;

/**
 * Persistent independently bindable WGPU sampler.
 */
final class WGPUSamplerHandle implements Sampler {
    private final WGPUResourceDomain resourceDomain;
    private final WGPUSamplerAllocation allocation;
    private final ShaderSamplerKind kind;

    WGPUSamplerHandle(WGPUResourceDomain resourceDomain,
            WGPUSamplerAllocation allocation, ShaderSamplerKind kind) {
        if (resourceDomain == null || allocation == null
                || allocation.resourceDomain() != resourceDomain) {
            throw new FdxException("WGPU sampler allocation is incompatible with its resource domain");
        }
        this.resourceDomain = resourceDomain;
        this.allocation = allocation;
        this.kind = kind;
    }

    WGPUResourceDomain resourceDomain() {
        return resourceDomain;
    }

    WGPUSamplerAllocation allocation() {
        return allocation;
    }

    ShaderSamplerKind kind() {
        return kind;
    }

    @Override
    public ProviderId providerId() {
        return WGPUProvider.ID;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T as() {
        return (T) this;
    }

    @Override
    public void dispose() {
        allocation.retire();
    }

    @Override
    public boolean isDisposed() {
        return allocation.isRetired();
    }
}
