package io.github.libfdx.graphics.gl;

import io.github.libfdx.core.ProviderId;
import io.github.libfdx.graphics.PrimitiveTopology;
import io.github.libfdx.graphics.RenderPipeline;
import io.github.libfdx.graphics.VertexLayout;

final class GLRenderPipelineHandle implements RenderPipeline {
    private final ProviderId providerId;
    private final int program;
    private final PrimitiveTopology primitiveTopology;
    private final VertexLayout[] vertexLayouts;
    private final int sampledTextureCount;
    private final boolean depthTestEnabled;
    private final boolean depthWriteEnabled;
    private boolean disposed;

    GLRenderPipelineHandle(ProviderId providerId, int program, PrimitiveTopology primitiveTopology,
            VertexLayout[] vertexLayouts, int sampledTextureCount, boolean depthTestEnabled,
            boolean depthWriteEnabled) {
        this.providerId = providerId;
        this.program = program;
        this.primitiveTopology = primitiveTopology != null ? primitiveTopology : PrimitiveTopology.TRIANGLE_LIST;
        this.vertexLayouts = vertexLayouts != null ? vertexLayouts.clone() : new VertexLayout[0];
        this.sampledTextureCount = sampledTextureCount;
        this.depthTestEnabled = depthTestEnabled;
        this.depthWriteEnabled = depthWriteEnabled;
    }

    int program() {
        return program;
    }

    PrimitiveTopology primitiveTopology() {
        return primitiveTopology;
    }

    VertexLayout vertexLayout() {
        return vertexLayouts.length > 0 ? vertexLayouts[0] : null;
    }

    VertexLayout[] vertexLayouts() {
        return vertexLayouts.clone();
    }

    int sampledTextureCount() {
        return sampledTextureCount;
    }

    boolean depthTestEnabled() {
        return depthTestEnabled;
    }

    boolean depthWriteEnabled() {
        return depthWriteEnabled;
    }

    @Override
    public ProviderId providerId() {
        return providerId;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T as() {
        return (T) this;
    }

    @Override
    public void dispose() {
        disposed = true;
    }

    @Override
    public boolean isDisposed() {
        return disposed;
    }
}
