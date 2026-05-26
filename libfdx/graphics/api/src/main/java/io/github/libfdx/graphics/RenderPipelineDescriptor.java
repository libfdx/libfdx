package io.github.libfdx.graphics;

import io.github.libfdx.core.FdxException;

public final class RenderPipelineDescriptor {
    private String label = "";
    private ShaderModule shaderModule;
    private String vertexEntryPoint = "vertexMain";
    private String fragmentEntryPoint = "fragmentMain";
    private TextureFormat colorFormat = TextureFormat.UNKNOWN;
    private PrimitiveTopology primitiveTopology = PrimitiveTopology.TRIANGLE_LIST;
    private VertexLayout[] vertexLayouts = new VertexLayout[0];
    private int sampledTextureCount;
    private boolean depthTestEnabled;
    private boolean depthWriteEnabled = true;

    public static RenderPipelineDescriptor shader(ShaderModule shaderModule, TextureFormat colorFormat) {
        return new RenderPipelineDescriptor()
                .shaderModule(shaderModule)
                .colorFormat(colorFormat);
    }

    public String label() {
        return label;
    }

    public RenderPipelineDescriptor label(String label) {
        this.label = label != null ? label : "";
        return this;
    }

    public ShaderModule shaderModule() {
        return shaderModule;
    }

    public RenderPipelineDescriptor shaderModule(ShaderModule shaderModule) {
        if (shaderModule == null) {
            throw new FdxException("Render pipeline shader module cannot be null");
        }
        this.shaderModule = shaderModule;
        return this;
    }

    public String vertexEntryPoint() {
        return vertexEntryPoint;
    }

    public RenderPipelineDescriptor vertexEntryPoint(String vertexEntryPoint) {
        this.vertexEntryPoint = vertexEntryPoint != null ? vertexEntryPoint : "";
        return this;
    }

    public String fragmentEntryPoint() {
        return fragmentEntryPoint;
    }

    public RenderPipelineDescriptor fragmentEntryPoint(String fragmentEntryPoint) {
        this.fragmentEntryPoint = fragmentEntryPoint != null ? fragmentEntryPoint : "";
        return this;
    }

    public TextureFormat colorFormat() {
        return colorFormat;
    }

    public RenderPipelineDescriptor colorFormat(TextureFormat colorFormat) {
        this.colorFormat = colorFormat != null ? colorFormat : TextureFormat.UNKNOWN;
        return this;
    }

    public PrimitiveTopology primitiveTopology() {
        return primitiveTopology;
    }

    public RenderPipelineDescriptor primitiveTopology(PrimitiveTopology primitiveTopology) {
        this.primitiveTopology = primitiveTopology != null ? primitiveTopology : PrimitiveTopology.TRIANGLE_LIST;
        return this;
    }

    public VertexLayout vertexLayout() {
        return vertexLayouts.length > 0 ? vertexLayouts[0] : null;
    }

    public VertexLayout[] vertexLayouts() {
        return vertexLayouts.clone();
    }

    public RenderPipelineDescriptor vertexLayout(VertexLayout vertexLayout) {
        this.vertexLayouts = vertexLayout != null ? new VertexLayout[] { vertexLayout } : new VertexLayout[0];
        return this;
    }

    public RenderPipelineDescriptor vertexLayouts(VertexLayout... vertexLayouts) {
        if (vertexLayouts == null || vertexLayouts.length == 0) {
            this.vertexLayouts = new VertexLayout[0];
            return this;
        }
        for (int i = 0; i < vertexLayouts.length; i++) {
            if (vertexLayouts[i] == null) {
                throw new FdxException("Render pipeline vertex layout cannot be null");
            }
        }
        this.vertexLayouts = vertexLayouts.clone();
        return this;
    }

    public int sampledTextureCount() {
        return sampledTextureCount;
    }

    public RenderPipelineDescriptor sampledTextureCount(int sampledTextureCount) {
        if (sampledTextureCount < 0) {
            throw new FdxException("Sampled texture count cannot be negative");
        }
        this.sampledTextureCount = sampledTextureCount;
        return this;
    }

    public boolean depthTestEnabled() {
        return depthTestEnabled;
    }

    public RenderPipelineDescriptor depthTestEnabled(boolean depthTestEnabled) {
        this.depthTestEnabled = depthTestEnabled;
        return this;
    }

    public boolean depthWriteEnabled() {
        return depthWriteEnabled;
    }

    public RenderPipelineDescriptor depthWriteEnabled(boolean depthWriteEnabled) {
        this.depthWriteEnabled = depthWriteEnabled;
        return this;
    }
}
