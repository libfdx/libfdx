package io.github.libfdx.graphics.shadergraph.technique;

import io.github.libfdx.core.FdxException;
import io.github.libfdx.graphics.BlendComponent;
import io.github.libfdx.graphics.BlendState;
import io.github.libfdx.graphics.ColorTargetState;
import io.github.libfdx.graphics.DepthStencilState;
import io.github.libfdx.graphics.GraphicsCapabilities;
import io.github.libfdx.graphics.MultisampleState;
import io.github.libfdx.graphics.PrimitiveState;
import io.github.libfdx.graphics.RenderTargetLayout;
import io.github.libfdx.graphics.TextureFormat;
import io.github.libfdx.graphics.VertexLayout;
import io.github.libfdx.graphics.internal.PortableSha256;

import java.util.Arrays;
import java.util.Objects;

/**
 * Complete immutable render-pipeline state owned by a technique pass.
 *
 * <p>Attachment formats and sample count are explicit. A renderer request
 * must match this state; it cannot silently mutate the pass.</p>
 */
public final class ShaderGraphPipelineState {
    private final PrimitiveState primitive;
    private final ColorTargetState[] colorTargets;
    private final DepthStencilState depthStencil;
    private final MultisampleState multisample;
    private final VertexLayout[] vertexLayouts;
    private final RenderTargetLayout targetLayout;
    private final String semanticHash;

    private ShaderGraphPipelineState(Builder builder) {
        primitive = builder.primitive != null
                ? builder.primitive : PrimitiveState.triangles();
        colorTargets = builder.colorTargets != null
                ? builder.colorTargets.clone() : new ColorTargetState[0];
        for (ColorTargetState target : colorTargets) {
            if (target == null) {
                throw new FdxException(
                        "Shader pass color target cannot be null");
            }
        }
        depthStencil = builder.depthStencil;
        multisample = builder.multisample != null
                ? builder.multisample : MultisampleState.singleSample();
        vertexLayouts = builder.vertexLayouts != null
                ? builder.vertexLayouts.clone() : new VertexLayout[0];
        for (VertexLayout layout : vertexLayouts) {
            if (layout == null) {
                throw new FdxException(
                        "Shader pass vertex layout cannot be null");
            }
        }
        if (colorTargets.length == 0 && depthStencil == null) {
            throw new FdxException(
                    "Shader pass state requires a color or depth target");
        }
        TextureFormat[] colors = new TextureFormat[colorTargets.length];
        for (int i = 0; i < colors.length; i++) {
            colors[i] = colorTargets[i].format();
        }
        targetLayout = RenderTargetLayout.of(colors,
                depthStencil != null ? depthStencil.format()
                        : TextureFormat.UNKNOWN,
                multisample.count());
        semanticHash = PortableSha256.hashUtf8(semanticKey());
    }

    public static Builder builder() {
        return new Builder();
    }

    public PrimitiveState primitive() {
        return primitive;
    }

    public ColorTargetState[] colorTargets() {
        return colorTargets.clone();
    }

    public DepthStencilState depthStencil() {
        return depthStencil;
    }

    public MultisampleState multisample() {
        return multisample;
    }

    public VertexLayout[] vertexLayouts() {
        return vertexLayouts.clone();
    }

    public RenderTargetLayout targetLayout() {
        return targetLayout;
    }

    public String semanticHash() {
        return semanticHash;
    }

    public void validate(RenderTargetLayout requested,
            VertexLayout[] requestedVertexLayouts,
            GraphicsCapabilities capabilities) {
        if (!targetLayout.equals(requested)) {
            throw new FdxException("Shader technique pass requires target "
                    + targetLayout.structuralKey() + ", requested "
                    + (requested != null ? requested.structuralKey() : "null"));
        }
        targetLayout.validate(capabilities);
        if (vertexLayouts.length > 0) {
            VertexLayout[] actual = requestedVertexLayouts != null
                    ? requestedVertexLayouts : new VertexLayout[0];
            if (!Arrays.equals(vertexLayouts, actual)) {
                throw new FdxException(
                        "Shader technique pass vertex layout does not match");
            }
        }
    }

    @Override
    public boolean equals(Object object) {
        return object instanceof ShaderGraphPipelineState other
                && primitive.equals(other.primitive)
                && Arrays.equals(colorTargets, other.colorTargets)
                && Objects.equals(depthStencil, other.depthStencil)
                && multisample.equals(other.multisample)
                && Arrays.equals(vertexLayouts, other.vertexLayouts);
    }

    @Override
    public int hashCode() {
        return Objects.hash(primitive, Arrays.hashCode(colorTargets),
                depthStencil, multisample, Arrays.hashCode(vertexLayouts));
    }

    private String semanticKey() {
        StringBuilder value = new StringBuilder("fdx-graph-state-v1\n")
                .append(primitive.topology().name()).append('\n')
                .append(primitive.frontFace().name()).append('\n')
                .append(primitive.cullMode().name()).append('\n')
                .append(colorTargets.length).append('\n');
        for (ColorTargetState target : colorTargets) {
            value.append(target.format().name()).append(':')
                    .append(target.writeMask()).append(':');
            appendBlend(value, target.blend());
            value.append('\n');
        }
        if (depthStencil == null) {
            value.append("no-depth\n");
        } else {
            value.append(depthStencil.format().name()).append(':')
                    .append(depthStencil.depthWriteEnabled()).append(':')
                    .append(depthStencil.depthCompare().name()).append(':')
                    .append(depthStencil.stencilFront().compare().name())
                    .append(':')
                    .append(depthStencil.stencilFront().fail().name())
                    .append(':')
                    .append(depthStencil.stencilFront().depthFail().name())
                    .append(':')
                    .append(depthStencil.stencilFront().pass().name())
                    .append(':')
                    .append(depthStencil.stencilBack().compare().name())
                    .append(':')
                    .append(depthStencil.stencilBack().fail().name())
                    .append(':')
                    .append(depthStencil.stencilBack().depthFail().name())
                    .append(':')
                    .append(depthStencil.stencilBack().pass().name())
                    .append(':').append(depthStencil.stencilReadMask())
                    .append(':').append(depthStencil.stencilWriteMask())
                    .append(':').append(depthStencil.depthBias())
                    .append(':').append(Float.floatToRawIntBits(
                            depthStencil.depthBiasSlopeScale()))
                    .append(':').append(Float.floatToRawIntBits(
                            depthStencil.depthBiasClamp()))
                    .append('\n');
        }
        value.append(multisample.count()).append(':')
                .append(multisample.mask()).append(':')
                .append(multisample.alphaToCoverageEnabled()).append('\n')
                .append(vertexLayouts.length).append('\n');
        for (VertexLayout layout : vertexLayouts) {
            value.append(layout.structuralKey()).append('\n');
        }
        return value.toString();
    }

    private static void appendBlend(StringBuilder value, BlendState blend) {
        if (blend == null) {
            value.append("disabled");
            return;
        }
        appendBlendComponent(value, blend.color());
        value.append('/');
        appendBlendComponent(value, blend.alpha());
    }

    private static void appendBlendComponent(StringBuilder value,
            BlendComponent component) {
        value.append(component.sourceFactor().name()).append(',')
                .append(component.destinationFactor().name()).append(',')
                .append(component.operation().name());
    }

    /**
     * Mutable state construction scope.
     */
    public static final class Builder {
        private PrimitiveState primitive;
        private ColorTargetState[] colorTargets = new ColorTargetState[0];
        private DepthStencilState depthStencil;
        private MultisampleState multisample;
        private VertexLayout[] vertexLayouts = new VertexLayout[0];

        private Builder() {
        }

        public Builder primitive(PrimitiveState value) {
            primitive = value;
            return this;
        }

        public Builder colorTargets(ColorTargetState... values) {
            colorTargets = values != null ? values
                    : new ColorTargetState[0];
            return this;
        }

        public Builder depthStencil(DepthStencilState value) {
            depthStencil = value;
            return this;
        }

        public Builder multisample(MultisampleState value) {
            multisample = value;
            return this;
        }

        public Builder vertexLayouts(VertexLayout... values) {
            vertexLayouts = values != null ? values : new VertexLayout[0];
            return this;
        }

        public ShaderGraphPipelineState build() {
            return new ShaderGraphPipelineState(this);
        }
    }
}
