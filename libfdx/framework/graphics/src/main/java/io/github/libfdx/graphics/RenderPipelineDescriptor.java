package io.github.libfdx.graphics;

import io.github.libfdx.core.FdxException;

/**
 * Describes the values used to create or identify a render pipeline.
 *
 * @author xpenatan
 */
public final class RenderPipelineDescriptor {
    private String label = "";
    private ShaderModule shaderModule;
    private String vertexEntryPoint = "vertexMain";
    private String fragmentEntryPoint = "fragmentMain";
    private TextureFormat colorFormat = TextureFormat.UNKNOWN;
    private ShaderReflection shaderReflection = ShaderReflection.empty();
    private PrimitiveTopology primitiveTopology = PrimitiveTopology.TRIANGLE_LIST;
    private VertexLayout[] vertexLayouts = new VertexLayout[0];
    private int sampledTextureCount;
    private boolean depthTestEnabled;
    private boolean depthWriteEnabled = true;

    /**
     * Creates a render pipeline descriptor.
     *
     * @param shaderModule the shader module
     * @param colorFormat the color format
     * @return a new render pipeline descriptor
     */
    public static RenderPipelineDescriptor shader(ShaderModule shaderModule, TextureFormat colorFormat) {
        return new RenderPipelineDescriptor()
                .shaderModule(shaderModule)
                .colorFormat(colorFormat);
    }

    /**
     * Returns the label.
     *
     * @return the label
     */
    public String label() {
        return label;
    }

    /**
     * Sets the label and returns this render pipeline descriptor.
     *
     * @param label the debug label
     * @return this render pipeline descriptor for chaining
     */
    public RenderPipelineDescriptor label(String label) {
        this.label = label != null ? label : "";
        return this;
    }

    /**
     * Returns the shader module.
     *
     * @return the shader module
     */
    public ShaderModule shaderModule() {
        return shaderModule;
    }

    /**
     * Sets the shader module and returns this render pipeline descriptor.
     *
     * @param shaderModule the shader module
     * @return this render pipeline descriptor for chaining
     */
    public RenderPipelineDescriptor shaderModule(ShaderModule shaderModule) {
        if (shaderModule == null) {
            throw new FdxException("Render pipeline shader module cannot be null");
        }
        this.shaderModule = shaderModule;
        return this;
    }

    /**
     * Returns the vertex entry point.
     *
     * @return the vertex entry point
     */
    public String vertexEntryPoint() {
        return vertexEntryPoint;
    }

    /**
     * Sets the vertex entry point and returns this render pipeline descriptor.
     *
     * @param vertexEntryPoint the vertex entry point
     * @return this render pipeline descriptor for chaining
     */
    public RenderPipelineDescriptor vertexEntryPoint(String vertexEntryPoint) {
        this.vertexEntryPoint = vertexEntryPoint != null ? vertexEntryPoint : "";
        return this;
    }

    /**
     * Returns the fragment entry point.
     *
     * @return the fragment entry point
     */
    public String fragmentEntryPoint() {
        return fragmentEntryPoint;
    }

    /**
     * Sets the fragment entry point and returns this render pipeline descriptor.
     *
     * @param fragmentEntryPoint the fragment entry point
     * @return this render pipeline descriptor for chaining
     */
    public RenderPipelineDescriptor fragmentEntryPoint(String fragmentEntryPoint) {
        this.fragmentEntryPoint = fragmentEntryPoint != null ? fragmentEntryPoint : "";
        return this;
    }

    /**
     * Returns the color format.
     *
     * @return the color format
     */
    public TextureFormat colorFormat() {
        return colorFormat;
    }

    /**
     * Sets the color format and returns this render pipeline descriptor.
     *
     * @param colorFormat the color format
     * @return this render pipeline descriptor for chaining
     */
    public RenderPipelineDescriptor colorFormat(TextureFormat colorFormat) {
        this.colorFormat = colorFormat != null ? colorFormat : TextureFormat.UNKNOWN;
        return this;
    }

    /**
     * Returns the shader reflection.
     *
     * @return the shader reflection
     */
    public ShaderReflection shaderReflection() {
        return shaderReflection;
    }

    /**
     * Sets the shader reflection and returns this render pipeline descriptor.
     *
     * @param shaderReflection the shader reflection
     * @return this render pipeline descriptor for chaining
     */
    public RenderPipelineDescriptor shaderReflection(ShaderReflection shaderReflection) {
        this.shaderReflection = shaderReflection != null ? shaderReflection : ShaderReflection.empty();
        return this;
    }

    /**
     * Returns the primitive topology.
     *
     * @return the primitive topology
     */
    public PrimitiveTopology primitiveTopology() {
        return primitiveTopology;
    }

    /**
     * Sets the primitive topology and returns this render pipeline descriptor.
     *
     * @param primitiveTopology the primitive topology
     * @return this render pipeline descriptor for chaining
     */
    public RenderPipelineDescriptor primitiveTopology(PrimitiveTopology primitiveTopology) {
        this.primitiveTopology = primitiveTopology != null ? primitiveTopology : PrimitiveTopology.TRIANGLE_LIST;
        return this;
    }

    /**
     * Returns the vertex layout.
     *
     * @return the vertex layout
     */
    public VertexLayout vertexLayout() {
        return vertexLayouts.length > 0 ? vertexLayouts[0] : null;
    }

    /**
     * Returns the vertex layouts.
     *
     * @return the vertex layouts
     */
    public VertexLayout[] vertexLayouts() {
        return vertexLayouts.clone();
    }

    /**
     * Sets the vertex layout and returns this render pipeline descriptor.
     *
     * @param vertexLayout the vertex layout
     * @return this render pipeline descriptor for chaining
     */
    public RenderPipelineDescriptor vertexLayout(VertexLayout vertexLayout) {
        this.vertexLayouts = vertexLayout != null ? new VertexLayout[] { vertexLayout } : new VertexLayout[0];
        return this;
    }

    /**
     * Sets the vertex layouts and returns this render pipeline descriptor.
     *
     * @param vertexLayouts the vertex layouts
     * @return this render pipeline descriptor for chaining
     */
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

    /**
     * Returns the sampled texture count.
     *
     * @return the sampled texture count
     */
    public int sampledTextureCount() {
        return sampledTextureCount;
    }

    /**
     * Sets the sampled texture count and returns this render pipeline descriptor.
     *
     * @param sampledTextureCount the sampled texture count
     * @return this render pipeline descriptor for chaining
     */
    public RenderPipelineDescriptor sampledTextureCount(int sampledTextureCount) {
        if (sampledTextureCount < 0) {
            throw new FdxException("Sampled texture count cannot be negative");
        }
        this.sampledTextureCount = sampledTextureCount;
        return this;
    }

    /**
     * Returns the depth test enabled.
     *
     * @return true if depth test enabled succeeds or is active; false otherwise
     */
    public boolean depthTestEnabled() {
        return depthTestEnabled;
    }

    /**
     * Sets the depth test enabled and returns this render pipeline descriptor.
     *
     * @param depthTestEnabled the depth test enabled
     * @return this render pipeline descriptor for chaining
     */
    public RenderPipelineDescriptor depthTestEnabled(boolean depthTestEnabled) {
        this.depthTestEnabled = depthTestEnabled;
        return this;
    }

    /**
     * Returns the depth write enabled.
     *
     * @return true if depth write enabled succeeds or is active; false otherwise
     */
    public boolean depthWriteEnabled() {
        return depthWriteEnabled;
    }

    /**
     * Sets the depth write enabled and returns this render pipeline descriptor.
     *
     * @param depthWriteEnabled the depth write enabled
     * @return this render pipeline descriptor for chaining
     */
    public RenderPipelineDescriptor depthWriteEnabled(boolean depthWriteEnabled) {
        this.depthWriteEnabled = depthWriteEnabled;
        return this;
    }
}
