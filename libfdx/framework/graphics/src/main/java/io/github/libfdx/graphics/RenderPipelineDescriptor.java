package io.github.libfdx.graphics;

import io.github.libfdx.graphics.shader.ShaderModule;
import io.github.libfdx.graphics.shader.reflection.ShaderBinding;
import io.github.libfdx.graphics.shader.reflection.ShaderBindingType;
import io.github.libfdx.graphics.shader.reflection.ShaderReflection;
import io.github.libfdx.graphics.shader.reflection.ShaderResourceLayout;
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
    private boolean shaderReflectionExplicit;
    private PrimitiveTopology primitiveTopology = PrimitiveTopology.TRIANGLE_LIST;
    private VertexLayout[] vertexLayouts = new VertexLayout[0];
    private int sampledTextureCount;
    private boolean sampledTextureCountExplicit;
    private boolean depthTestEnabled;
    private boolean depthWriteEnabled = true;
    private RenderTargetLayout renderTargetLayout;
    private ShaderResourceLayout resourceLayout;
    private PrimitiveState primitiveState = PrimitiveState.triangles();
    private ColorTargetState[] colorTargets;
    private DepthStencilState depthStencilState;
    private MultisampleState multisampleState;

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
        renderTargetLayout = null;
        return this;
    }

    /**
     * Returns the complete render-target layout when supplied, otherwise a
     * source-compatible single-color layout.
     *
     * @return render-target layout
     */
    public RenderTargetLayout renderTargetLayout() {
        if (renderTargetLayout != null) {
            return renderTargetLayout;
        }
        TextureFormat depthFormat = depthStencilState != null
                ? depthStencilState.format()
                : depthTestEnabled ? TextureFormat.DEPTH32_FLOAT : TextureFormat.UNKNOWN;
        if (colorFormat == TextureFormat.UNKNOWN && depthFormat == TextureFormat.UNKNOWN) {
            throw new FdxException("Render pipeline target format is unknown");
        }
        TextureFormat[] colors = colorFormat != TextureFormat.UNKNOWN
                ? new TextureFormat[] { colorFormat } : new TextureFormat[0];
        int samples = multisampleState != null ? multisampleState.count() : 1;
        return RenderTargetLayout.of(colors, depthFormat, samples);
    }

    /**
     * Sets complete color/depth/stencil/sample compatibility.
     *
     * @param layout render-target layout
     * @return this descriptor
     */
    public RenderPipelineDescriptor renderTargetLayout(RenderTargetLayout layout) {
        if (layout == null) {
            throw new FdxException("Render pipeline target layout cannot be null");
        }
        renderTargetLayout = layout;
        colorFormat = layout.colorAttachmentCount() > 0
                ? layout.colorFormat(0) : TextureFormat.UNKNOWN;
        return this;
    }

    /**
     * Returns the pipeline sample count.
     *
     * @return sample count
     */
    public int sampleCount() {
        return renderTargetLayout().sampleCount();
    }

    /**
     * Returns the shader reflection.
     *
     * @return the shader reflection
     */
    public ShaderReflection shaderReflection() {
        ShaderReflection moduleReflection = shaderModule != null
                ? shaderModule.reflection() : ShaderReflection.empty();
        if (!shaderReflectionExplicit) {
            return moduleReflection;
        }
        if (shaderReflection == ShaderReflection.empty()) {
            return shaderReflection;
        }
        if (moduleReflection.complete()) {
            if (shaderReflection.complete()) {
                if (!moduleReflection.physicallyEquivalent(shaderReflection)) {
                    throw new FdxException("Render pipeline shader reflection does not match its shader module");
                }
                return shaderReflection;
            }
            validateCompatibilitySubset(shaderReflection, moduleReflection);
            return moduleReflection;
        }
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
        shaderReflectionExplicit = true;
        return this;
    }

    /**
     * Returns the explicit or reflection-derived shader resource layout.
     *
     * @return resource layout
     */
    public ShaderResourceLayout resourceLayout() {
        if (resourceLayout != null) {
            return resourceLayout;
        }
        ShaderReflection reflection = shaderReflection();
        return reflection.complete()
                ? ShaderResourceLayout.render(reflection, vertexEntryPoint, fragmentEntryPoint)
                : null;
    }

    /**
     * Sets an explicit resource layout and verifies it against module
     * reflection when complete reflection is available.
     *
     * @param layout resource layout
     * @return this descriptor
     */
    public RenderPipelineDescriptor resourceLayout(ShaderResourceLayout layout) {
        if (layout == null) {
            throw new FdxException("Render pipeline resource layout cannot be null");
        }
        ShaderReflection reflection = shaderReflection();
        if (reflection.complete()
                && !reflection.physicallyEquivalent(layout.reflection())) {
            throw new FdxException("Render pipeline resource layout does not match shader reflection");
        }
        resourceLayout = layout;
        return this;
    }

    /**
     * Returns whether reflection was explicitly supplied instead of inherited from the module.
     *
     * @return whether reflection is explicit
     */
    public boolean hasExplicitShaderReflection() {
        return shaderReflectionExplicit;
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
        primitiveState = primitiveState.withTopology(this.primitiveTopology);
        return this;
    }

    /**
     * Returns complete primitive/raster state.
     *
     * @return primitive state
     */
    public PrimitiveState primitiveState() {
        return primitiveState;
    }

    /**
     * Sets complete primitive/raster state.
     *
     * @param value primitive state
     * @return this descriptor
     */
    public RenderPipelineDescriptor primitiveState(PrimitiveState value) {
        if (value == null) {
            throw new FdxException("Render pipeline primitive state cannot be null");
        }
        primitiveState = value;
        primitiveTopology = value.topology();
        return this;
    }

    /**
     * Returns color target states, deriving source-compatible alpha blending
     * for each target when not set explicitly.
     *
     * @return color target states
     */
    public ColorTargetState[] colorTargets() {
        if (colorTargets != null) {
            return colorTargets.clone();
        }
        RenderTargetLayout targets = renderTargetLayout();
        ColorTargetState[] result = new ColorTargetState[targets.colorAttachmentCount()];
        for (int i = 0; i < result.length; i++) {
            result[i] = ColorTargetState.alpha(targets.colorFormat(i));
        }
        return result;
    }

    /**
     * Sets complete per-target blend and write state.
     *
     * @param values color target states
     * @return this descriptor
     */
    public RenderPipelineDescriptor colorTargets(ColorTargetState... values) {
        if (values == null) {
            throw new FdxException("Render pipeline color targets cannot be null");
        }
        for (ColorTargetState value : values) {
            if (value == null) {
                throw new FdxException("Render pipeline color target cannot be null");
            }
        }
        colorTargets = values.clone();
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
        if (sampledTextureCountExplicit) {
            ShaderReflection reflection = shaderReflection();
            if (reflection.complete()) {
                int derived = reflection.sampledTextureCount(vertexEntryPoint, fragmentEntryPoint);
                if (sampledTextureCount != derived) {
                    throw new FdxException("Explicit sampled texture count " + sampledTextureCount
                            + " does not match reflected count " + derived);
                }
            }
            return sampledTextureCount;
        }
        return shaderReflection().sampledTextureCount(vertexEntryPoint, fragmentEntryPoint);
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
        sampledTextureCountExplicit = true;
        return this;
    }

    /**
     * Returns whether the sampled texture count was explicitly supplied.
     *
     * @return whether the count is explicit
     */
    public boolean hasExplicitSampledTextureCount() {
        return sampledTextureCountExplicit;
    }

    private static void validateCompatibilitySubset(ShaderReflection coarse, ShaderReflection complete) {
        for (ShaderBinding binding : coarse.bindings()) {
            ShaderBinding reflected = complete.findBinding(binding.group(), binding.binding());
            if (reflected == null || binding.type() != ShaderBindingType.UNKNOWN
                    && binding.type() != reflected.type()) {
                throw new FdxException("Render pipeline compatibility reflection does not match module binding "
                        + binding.group() + ':' + binding.binding());
            }
        }
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
        depthStencilState = null;
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
        depthStencilState = null;
        return this;
    }

    /**
     * Returns explicit depth/stencil state, or a source-compatible derived
     * state when depth testing is enabled.
     *
     * @return depth/stencil state, or {@code null}
     */
    public DepthStencilState depthStencilState() {
        if (depthStencilState != null) {
            return depthStencilState;
        }
        if (!depthTestEnabled) {
            return null;
        }
        TextureFormat format = renderTargetLayout != null
                && renderTargetLayout.hasDepthStencil()
                ? renderTargetLayout.depthStencilFormat() : TextureFormat.DEPTH32_FLOAT;
        return DepthStencilState.depth(format, depthWriteEnabled);
    }

    /**
     * Sets complete depth/stencil state. Passing {@code null} disables it.
     *
     * @param value depth/stencil state
     * @return this descriptor
     */
    public RenderPipelineDescriptor depthStencilState(DepthStencilState value) {
        depthStencilState = value;
        depthTestEnabled = value != null;
        depthWriteEnabled = value != null && value.depthWriteEnabled();
        return this;
    }

    /**
     * Returns complete multisample state.
     *
     * @return multisample state
     */
    public MultisampleState multisampleState() {
        return multisampleState != null ? multisampleState
                : MultisampleState.of(renderTargetLayout != null
                        ? renderTargetLayout.sampleCount() : 1, -1, false);
    }

    /**
     * Sets complete multisample state.
     *
     * @param value multisample state
     * @return this descriptor
     */
    public RenderPipelineDescriptor multisampleState(MultisampleState value) {
        if (value == null) {
            throw new FdxException("Render pipeline multisample state cannot be null");
        }
        multisampleState = value;
        return this;
    }

    /**
     * Validates resource, target, and vertex limits before native pipeline
     * creation.
     *
     * @param capabilities device capabilities
     */
    public void validate(GraphicsCapabilities capabilities) {
        if (capabilities == null) {
            throw new FdxException("Graphics capabilities cannot be null");
        }
        if (shaderModule == null) {
            throw new FdxException("Render pipeline shader module cannot be null");
        }
        renderTargetLayout().validate(capabilities);
        ColorTargetState[] targets = colorTargets();
        if (targets.length != renderTargetLayout().colorAttachmentCount()) {
            throw new FdxException("Render pipeline color state count does not match target layout");
        }
        for (int i = 0; i < targets.length; i++) {
            if (targets[i].format() != renderTargetLayout().colorFormat(i)) {
                throw new FdxException("Render pipeline color state format does not match target layout");
            }
        }
        DepthStencilState depthStencil = depthStencilState();
        if (depthStencil != null) {
            if (!renderTargetLayout().hasDepthStencil()) {
                throw new FdxException("Render pipeline depth/stencil state requires a depth/stencil target");
            }
            if (depthStencil.format() != renderTargetLayout().depthStencilFormat()) {
                throw new FdxException("Render pipeline depth/stencil state format does not match target layout");
            }
        }
        if (multisampleState().count() != renderTargetLayout().sampleCount()) {
            throw new FdxException("Render pipeline multisample state does not match target layout");
        }
        if (requiresCompleteRenderPipelineState()) {
            if (!requiresOnlyAlphaBlendControl()
                    || !capabilities.supports(
                    GraphicsFeature.ALPHA_BLEND_CONTROL)) {
                capabilities.require(
                        GraphicsFeature.COMPLETE_RENDER_PIPELINE_STATE);
            }
        }
        ShaderResourceLayout resources = resourceLayout();
        if (resources != null) {
            resources.validate(capabilities);
        }
        if (vertexLayouts.length > capabilities.limits().maxVertexBuffers()) {
            throw new FdxException("Render pipeline exceeds the provider vertex-buffer limit");
        }
        int attributeCount = 0;
        for (VertexLayout layout : vertexLayouts) {
            attributeCount += layout.attributeCount();
        }
        if (attributeCount > capabilities.limits().maxVertexAttributes()) {
            throw new FdxException("Render pipeline exceeds the provider vertex-attribute limit");
        }
    }

    private boolean requiresCompleteRenderPipelineState() {
        if (primitiveState.frontFace() != FrontFace.COUNTER_CLOCKWISE
                || primitiveState.cullMode() != CullMode.NONE) {
            return true;
        }
        for (ColorTargetState target : colorTargets()) {
            if (!BlendState.alphaBlend().equals(target.blend())
                    || target.writeMask() != ColorWriteMask.ALL) {
                return true;
            }
        }
        DepthStencilState depth = depthStencilState();
        if (depth != null && (depth.depthCompare() != CompareFunction.LESS_EQUAL
                || !StencilFaceState.disabled().equals(depth.stencilFront())
                || !StencilFaceState.disabled().equals(depth.stencilBack())
                || depth.stencilReadMask() != -1 || depth.stencilWriteMask() != -1
                || depth.depthBias() != 0
                || Float.floatToRawIntBits(depth.depthBiasSlopeScale()) != 0
                || Float.floatToRawIntBits(depth.depthBiasClamp()) != 0)) {
            return true;
        }
        MultisampleState multisample = multisampleState();
        return multisample.mask() != -1 || multisample.alphaToCoverageEnabled();
    }

    private boolean requiresOnlyAlphaBlendControl() {
        if (primitiveState.frontFace() != FrontFace.COUNTER_CLOCKWISE
                || primitiveState.cullMode() != CullMode.NONE) {
            return false;
        }
        boolean opaqueTarget = false;
        for (ColorTargetState target : colorTargets()) {
            if (target.writeMask() != ColorWriteMask.ALL) {
                return false;
            }
            if (target.blend() == null) {
                opaqueTarget = true;
            }
            else if (!BlendState.alphaBlend().equals(target.blend())) {
                return false;
            }
        }
        DepthStencilState depth = depthStencilState();
        if (depth != null && (depth.depthCompare() != CompareFunction.LESS_EQUAL
                || !StencilFaceState.disabled().equals(depth.stencilFront())
                || !StencilFaceState.disabled().equals(depth.stencilBack())
                || depth.stencilReadMask() != -1 || depth.stencilWriteMask() != -1
                || depth.depthBias() != 0
                || Float.floatToRawIntBits(depth.depthBiasSlopeScale()) != 0
                || Float.floatToRawIntBits(depth.depthBiasClamp()) != 0)) {
            return false;
        }
        MultisampleState multisample = multisampleState();
        return opaqueTarget && multisample.mask() == -1
                && !multisample.alphaToCoverageEnabled();
    }
}
