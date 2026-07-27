package io.github.libfdx.graphics.shader.runtime;

import io.github.libfdx.graphics.PrimitiveTopology;
import io.github.libfdx.graphics.RenderPassCompatibility;
import io.github.libfdx.graphics.VertexLayout;
import io.github.libfdx.graphics.shader.ShaderProfile;
import io.github.libfdx.core.FdxException;
import io.github.libfdx.graphics.internal.ShaderStableId;

/**
 * Immutable provider-neutral request for one shader technique pass.
 */
public final class ShaderRequest {
    private final ShaderPassId passId;
    private final ShaderProfile profile;
    private final RenderPassCompatibility renderPass;
    private final PrimitiveTopology topology;
    private final VertexLayout[] vertexLayouts;
    private final String variantKey;

    private ShaderRequest(Builder builder) {
        passId = builder.passId != null ? builder.passId : ShaderPassId.FORWARD;
        profile = builder.profile != null ? builder.profile : ShaderProfile.PORTABLE_WEBGL2;
        renderPass = builder.renderPass;
        topology = builder.topology != null ? builder.topology : PrimitiveTopology.TRIANGLE_LIST;
        vertexLayouts = builder.vertexLayouts != null
                ? builder.vertexLayouts.clone() : new VertexLayout[0];
        for (VertexLayout layout : vertexLayouts) {
            if (layout == null) {
                throw new FdxException("Shader request vertex layout cannot be null");
            }
        }
        variantKey = builder.variantKey != null && !builder.variantKey.trim().isEmpty()
                ? ShaderStableId.normalize(builder.variantKey, "Shader variant key") : "";
    }

    public static Builder builder(ShaderPassId passId) {
        return new Builder(passId);
    }

    public ShaderPassId passId() {
        return passId;
    }

    public ShaderProfile profile() {
        return profile;
    }

    public RenderPassCompatibility renderPass() {
        return renderPass;
    }

    public PrimitiveTopology topology() {
        return topology;
    }

    public VertexLayout[] vertexLayouts() {
        return vertexLayouts.clone();
    }

    public String variantKey() {
        return variantKey;
    }

    /**
     * Builds immutable shader requests.
     */
    public static final class Builder {
        private final ShaderPassId passId;
        private ShaderProfile profile;
        private RenderPassCompatibility renderPass;
        private PrimitiveTopology topology;
        private VertexLayout[] vertexLayouts;
        private String variantKey;

        private Builder(ShaderPassId passId) {
            this.passId = passId;
        }

        public Builder profile(ShaderProfile value) {
            profile = value;
            return this;
        }

        public Builder renderPass(RenderPassCompatibility value) {
            renderPass = value;
            return this;
        }

        public Builder topology(PrimitiveTopology value) {
            topology = value;
            return this;
        }

        public Builder vertexLayouts(VertexLayout... values) {
            vertexLayouts = values;
            return this;
        }

        public Builder variantKey(String value) {
            variantKey = value;
            return this;
        }

        public ShaderRequest build() {
            return new ShaderRequest(this);
        }
    }
}
