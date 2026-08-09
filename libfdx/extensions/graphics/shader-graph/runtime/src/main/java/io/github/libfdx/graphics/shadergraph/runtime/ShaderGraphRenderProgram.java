package io.github.libfdx.graphics.shadergraph.runtime;

import io.github.libfdx.core.FdxException;
import io.github.libfdx.graphics.CompareFunction;
import io.github.libfdx.graphics.CullMode;
import io.github.libfdx.graphics.FrontFace;
import io.github.libfdx.graphics.shader.ShaderModuleDescriptor;
import io.github.libfdx.graphics.shader.runtime.ShaderPassId;
import io.github.libfdx.graphics.shader.runtime.ShaderResourceBinding;
import io.github.libfdx.graphics.VertexLayout;
import io.github.libfdx.graphics.shadergraph.compiler.ShaderGraphCompileResult;

/**
 * One immutable linked render-program definition consumed by the graph
 * runtime provider.
 */
public final class ShaderGraphRenderProgram {
    private final String label;
    private final ShaderPassId passId;
    private final ShaderModuleDescriptor shader;
    private final String vertexEntryPoint;
    private final String fragmentEntryPoint;
    private final FrontFace frontFace;
    private final CullMode cullMode;
    private final boolean depthWrite;
    private final CompareFunction depthCompare;
    private final boolean alphaBlend;
    private final int cacheCapacity;
    private final VertexLayout[] vertexLayouts;
    private final ShaderResourceBinding defaultResources;

    private ShaderGraphRenderProgram(Builder builder) {
        if (builder.passId == null || builder.shader == null
                || builder.cacheCapacity <= 0) {
            throw new FdxException("Shader graph render program is incomplete");
        }
        label = builder.label != null ? builder.label : "";
        passId = builder.passId;
        shader = builder.shader;
        vertexEntryPoint = builder.vertexEntryPoint != null
                ? builder.vertexEntryPoint : shader.vertexEntryPoint();
        fragmentEntryPoint = builder.fragmentEntryPoint != null
                ? builder.fragmentEntryPoint : shader.fragmentEntryPoint();
        frontFace = builder.frontFace != null
                ? builder.frontFace : FrontFace.COUNTER_CLOCKWISE;
        cullMode = builder.cullMode != null ? builder.cullMode : CullMode.NONE;
        depthWrite = builder.depthWrite;
        depthCompare = builder.depthCompare != null
                ? builder.depthCompare : CompareFunction.LESS_EQUAL;
        alphaBlend = builder.alphaBlend;
        cacheCapacity = builder.cacheCapacity;
        vertexLayouts = builder.vertexLayouts != null
                ? builder.vertexLayouts.clone()
                : new VertexLayout[0];
        defaultResources = builder.defaultResources;
        for (VertexLayout layout : vertexLayouts) {
            if (layout == null) {
                throw new FdxException(
                        "Shader graph render-program vertex layout cannot be null");
            }
        }
    }

    public static Builder builder(ShaderPassId passId,
            ShaderModuleDescriptor shader) {
        return new Builder(passId, shader);
    }

    public static ShaderGraphRenderProgram surface(
            ShaderGraphCompileResult compilation) {
        if (compilation == null || !compilation.success()) {
            throw new FdxException("Surface render program requires successful graph compilation");
        }
        return builder(ShaderPassId.FORWARD,
                ShaderModuleDescriptor.wgsl("shader graph surface",
                                compilation.wgsl())
                        .entryPoints("fdx_graph_vertex", "fdx_graph_fragment"))
                .build();
    }

    public String label() {
        return label;
    }

    public ShaderPassId passId() {
        return passId;
    }

    public ShaderModuleDescriptor shader() {
        return shader;
    }

    public String vertexEntryPoint() {
        return vertexEntryPoint;
    }

    public String fragmentEntryPoint() {
        return fragmentEntryPoint;
    }

    public FrontFace frontFace() {
        return frontFace;
    }

    public CullMode cullMode() {
        return cullMode;
    }

    public boolean depthWrite() {
        return depthWrite;
    }

    public CompareFunction depthCompare() {
        return depthCompare;
    }

    /**
     * Returns whether source-alpha blending is enabled for color targets.
     *
     * @return true for alpha blending; false for opaque replacement
     */
    public boolean alphaBlend() {
        return alphaBlend;
    }

    public int cacheCapacity() {
        return cacheCapacity;
    }

    /**
     * Returns optional exact structural vertex layouts accepted by this
     * program. An empty array permits renderer-selected layouts.
     *
     * @return defensive layout-array copy
     */
    public VertexLayout[] vertexLayouts() {
        return vertexLayouts.clone();
    }

    /**
     * Returns the optional provider-owned default resource binding.
     *
     * @return borrowed default binding, or {@code null}
     */
    public ShaderResourceBinding defaultResources() {
        return defaultResources;
    }

    /**
     * Mutable construction scope.
     */
    public static final class Builder {
        private String label;
        private final ShaderPassId passId;
        private final ShaderModuleDescriptor shader;
        private String vertexEntryPoint;
        private String fragmentEntryPoint;
        private FrontFace frontFace;
        private CullMode cullMode;
        private boolean depthWrite = true;
        private CompareFunction depthCompare;
        private boolean alphaBlend = true;
        private int cacheCapacity = 64;
        private VertexLayout[] vertexLayouts = new VertexLayout[0];
        private ShaderResourceBinding defaultResources;

        private Builder(ShaderPassId passId, ShaderModuleDescriptor shader) {
            this.passId = passId;
            this.shader = shader;
        }

        public Builder label(String value) {
            label = value;
            return this;
        }

        public Builder entryPoints(String vertex, String fragment) {
            vertexEntryPoint = vertex;
            fragmentEntryPoint = fragment;
            return this;
        }

        public Builder raster(FrontFace front, CullMode cull) {
            frontFace = front;
            cullMode = cull;
            return this;
        }

        public Builder depth(boolean write, CompareFunction compare) {
            depthWrite = write;
            depthCompare = compare;
            return this;
        }

        /**
         * Selects source-alpha blending or opaque color replacement.
         *
         * @param value true for alpha blending; false for opaque output
         * @return this builder
         */
        public Builder alphaBlend(boolean value) {
            alphaBlend = value;
            return this;
        }

        public Builder cacheCapacity(int value) {
            cacheCapacity = value;
            return this;
        }

        /**
         * Declares the exact renderer input ABI for this program.
         *
         * @param values vertex layouts
         * @return this builder
         */
        public Builder vertexLayouts(VertexLayout... values) {
            vertexLayouts = values != null ? values
                    : new VertexLayout[0];
            return this;
        }

        /**
         * Supplies resources used when a renderer draw has no explicit
         * material/resource binding.
         *
         * @param value borrowed default binding
         * @return this builder
         */
        public Builder defaultResources(ShaderResourceBinding value) {
            defaultResources = value;
            return this;
        }

        public ShaderGraphRenderProgram build() {
            return new ShaderGraphRenderProgram(this);
        }
    }
}
