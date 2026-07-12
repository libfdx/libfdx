package io.github.libfdx.graphics.gl;

import io.github.libfdx.core.ProviderId;
import io.github.libfdx.graphics.PrimitiveTopology;
import io.github.libfdx.graphics.RenderPipeline;
import io.github.libfdx.graphics.VertexLayout;

/**
 * Represents a GL render pipeline handle.
 *
 * @author xpenatan
 */
final class GLRenderPipelineHandle implements RenderPipeline {
    static final int PBR_UNIFORM_BYTE_COUNT = 5232;

    private final ProviderId providerId;
    private final GLApi gl;
    private final GLResourceDomain resourceDomain;
    private final GLShaderModuleHandle shaderModule;
    private final PrimitiveTopology primitiveTopology;
    private final VertexLayout[] vertexLayouts;
    private final int sampledTextureCount;
    private final boolean depthTestEnabled;
    private final boolean depthWriteEnabled;
    private final int pbrUniformBuffer;
    private boolean disposed;

    GLRenderPipelineHandle(ProviderId providerId, GLApi gl, GLResourceDomain resourceDomain,
            GLShaderModuleHandle shaderModule, PrimitiveTopology primitiveTopology,
            VertexLayout[] vertexLayouts, int sampledTextureCount, boolean depthTestEnabled,
            boolean depthWriteEnabled, int pbrUniformBuffer) {
        this.providerId = providerId;
        this.gl = gl;
        this.resourceDomain = resourceDomain;
        this.shaderModule = shaderModule;
        this.primitiveTopology = primitiveTopology != null ? primitiveTopology : PrimitiveTopology.TRIANGLE_LIST;
        this.vertexLayouts = vertexLayouts != null ? vertexLayouts.clone() : new VertexLayout[0];
        this.sampledTextureCount = sampledTextureCount;
        this.depthTestEnabled = depthTestEnabled;
        this.depthWriteEnabled = depthWriteEnabled;
        this.pbrUniformBuffer = pbrUniformBuffer;
        shaderModule.retainForPipeline();
    }

    int program() {
        return shaderModule.program();
    }

    GLResourceDomain resourceDomain() {
        return resourceDomain;
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

    int vertexLayoutCount() {
        return vertexLayouts.length;
    }

    VertexLayout vertexLayout(int index) {
        return vertexLayouts[index];
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

    int pbrUniformBuffer() {
        return pbrUniformBuffer;
    }

    boolean pbrUniformBufferEnabled() {
        return pbrUniformBuffer != 0;
    }

    /**
     * Returns the identifier of the provider backing this object.
     *
     * @return the provider ID
     */
    @Override
    public ProviderId providerId() {
        return providerId;
    }

    /**
     * Returns the provider-specific representation requested by the caller.
     *
     * @param <T> the value type
     * @return the as
     */
    @Override
    @SuppressWarnings("unchecked")
    public <T> T as() {
        return (T) this;
    }

    /**
     * Releases resources held by this instance.
     */
    @Override
    public void dispose() {
        if (disposed) {
            return;
        }
        if (pbrUniformBuffer != 0) {
            if (resourceDomain.makeAnyContextCurrent()) {
                gl.deleteBuffer(pbrUniformBuffer);
            }
        }
        disposed = true;
        shaderModule.releaseFromPipeline();
    }

    /**
     * Returns whether this instance has already been disposed.
     *
     * @return true if disposed is enabled or true; false otherwise
     */
    @Override
    public boolean isDisposed() {
        return disposed;
    }
}
