package io.github.libfdx.graphics.shader.runtime;

import io.github.libfdx.graphics.RenderPipelineDescriptor;
import io.github.libfdx.graphics.RenderTargetLayout;
import io.github.libfdx.graphics.shader.ShaderModule;
import io.github.libfdx.graphics.shader.ShaderModuleDescriptor;
import io.github.libfdx.graphics.shader.ShaderModuleSource;
import java.util.Objects;

/**
 * Immutable source and complete pipeline-state packet, containing no native module, frame,
 * pass, encoder, or attachment view. Capture at content/configuration changes. Descriptor
 * accessors create private mutable copies for the provider job; never call them per draw.
 */
public final class ShaderPipelineRequest {
    private final ShaderModuleSource source;
    private final RenderPipelineDescriptor state;
    private final ShaderPassId passId;
    private final long revision;

    public ShaderPipelineRequest(ShaderModuleDescriptor source, RenderPipelineDescriptor state,
            ShaderPassId passId, long revision) {
        this(ShaderModuleSource.fixed(source), state, passId, revision);
    }

    public ShaderPipelineRequest(ShaderModuleSource source, RenderPipelineDescriptor state,
            ShaderPassId passId, long revision) {
        this.source = Objects.requireNonNull(source, "source");
        this.state = Objects.requireNonNull(state, "state").snapshotState();
        this.passId = Objects.requireNonNull(passId, "passId");
        if (revision < 0) throw new IllegalArgumentException("revision cannot be negative");
        this.revision = revision;
    }

    /** Invokes source generation on the provider's CPU preparation execution path. */
    public ShaderModuleDescriptor sourceDescriptor() { return source.generate(); }
    public RenderPipelineDescriptor pipelineDescriptor(ShaderModule preparedModule) {
        return state.snapshotState().shaderModule(Objects.requireNonNull(preparedModule, "preparedModule"));
    }
    public RenderTargetLayout targetLayout() { return state.renderTargetLayout(); }
    public ShaderPassId passId() { return passId; }
    public long providerRevision() { return revision; }
}
