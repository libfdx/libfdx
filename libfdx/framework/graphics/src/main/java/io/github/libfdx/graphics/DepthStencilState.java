package io.github.libfdx.graphics;

import io.github.libfdx.core.FdxException;

import java.util.Objects;

/**
 * Immutable depth/stencil pipeline state.
 */
public final class DepthStencilState {
    private final TextureFormat format;
    private final boolean depthWriteEnabled;
    private final CompareFunction depthCompare;
    private final StencilFaceState stencilFront;
    private final StencilFaceState stencilBack;
    private final int stencilReadMask;
    private final int stencilWriteMask;
    private final int depthBias;
    private final float depthBiasSlopeScale;
    private final float depthBiasClamp;

    private DepthStencilState(Builder builder) {
        if (builder.format == null || !builder.format.isDepthStencil()) {
            throw new FdxException("Depth/stencil state requires a depth/stencil format");
        }
        format = builder.format;
        depthWriteEnabled = builder.depthWriteEnabled;
        depthCompare = builder.depthCompare != null
                ? builder.depthCompare : CompareFunction.LESS_EQUAL;
        stencilFront = builder.stencilFront != null
                ? builder.stencilFront : StencilFaceState.disabled();
        stencilBack = builder.stencilBack != null
                ? builder.stencilBack : StencilFaceState.disabled();
        stencilReadMask = builder.stencilReadMask;
        stencilWriteMask = builder.stencilWriteMask;
        depthBias = builder.depthBias;
        depthBiasSlopeScale = builder.depthBiasSlopeScale;
        depthBiasClamp = builder.depthBiasClamp;
    }

    public static Builder builder(TextureFormat format) {
        return new Builder(format);
    }

    public static DepthStencilState depth(TextureFormat format, boolean writeEnabled) {
        return builder(format).depthWriteEnabled(writeEnabled)
                .depthCompare(CompareFunction.LESS_EQUAL).build();
    }

    public TextureFormat format() {
        return format;
    }

    public boolean depthWriteEnabled() {
        return depthWriteEnabled;
    }

    public CompareFunction depthCompare() {
        return depthCompare;
    }

    public StencilFaceState stencilFront() {
        return stencilFront;
    }

    public StencilFaceState stencilBack() {
        return stencilBack;
    }

    public int stencilReadMask() {
        return stencilReadMask;
    }

    public int stencilWriteMask() {
        return stencilWriteMask;
    }

    public int depthBias() {
        return depthBias;
    }

    public float depthBiasSlopeScale() {
        return depthBiasSlopeScale;
    }

    public float depthBiasClamp() {
        return depthBiasClamp;
    }

    @Override
    public boolean equals(Object object) {
        if (!(object instanceof DepthStencilState other)) {
            return false;
        }
        return format == other.format && depthWriteEnabled == other.depthWriteEnabled
                && depthCompare == other.depthCompare
                && stencilFront.equals(other.stencilFront)
                && stencilBack.equals(other.stencilBack)
                && stencilReadMask == other.stencilReadMask
                && stencilWriteMask == other.stencilWriteMask
                && depthBias == other.depthBias
                && Float.floatToRawIntBits(depthBiasSlopeScale)
                == Float.floatToRawIntBits(other.depthBiasSlopeScale)
                && Float.floatToRawIntBits(depthBiasClamp)
                == Float.floatToRawIntBits(other.depthBiasClamp);
    }

    @Override
    public int hashCode() {
        return Objects.hash(format, depthWriteEnabled, depthCompare,
                stencilFront, stencilBack, stencilReadMask, stencilWriteMask,
                depthBias, Float.floatToRawIntBits(depthBiasSlopeScale),
                Float.floatToRawIntBits(depthBiasClamp));
    }

    /**
     * Builds immutable depth/stencil state.
     */
    public static final class Builder {
        private final TextureFormat format;
        private boolean depthWriteEnabled;
        private CompareFunction depthCompare;
        private StencilFaceState stencilFront;
        private StencilFaceState stencilBack;
        private int stencilReadMask = -1;
        private int stencilWriteMask = -1;
        private int depthBias;
        private float depthBiasSlopeScale;
        private float depthBiasClamp;

        private Builder(TextureFormat format) {
            this.format = format;
        }

        public Builder depthWriteEnabled(boolean value) {
            depthWriteEnabled = value;
            return this;
        }

        public Builder depthCompare(CompareFunction value) {
            depthCompare = value;
            return this;
        }

        public Builder stencil(StencilFaceState front, StencilFaceState back,
                int readMask, int writeMask) {
            stencilFront = front;
            stencilBack = back;
            stencilReadMask = readMask;
            stencilWriteMask = writeMask;
            return this;
        }

        public Builder depthBias(int constant, float slopeScale, float clamp) {
            depthBias = constant;
            depthBiasSlopeScale = slopeScale;
            depthBiasClamp = clamp;
            return this;
        }

        public DepthStencilState build() {
            return new DepthStencilState(this);
        }
    }
}
