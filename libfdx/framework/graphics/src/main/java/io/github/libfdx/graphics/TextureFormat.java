package io.github.libfdx.graphics;

/**
 * Lists the supported texture format values.
 *
 * @author xpenatan
 */
public enum TextureFormat {
    UNKNOWN(false, false),
    RGBA8_UNORM(true, false),
    RGBA8_UNORM_SRGB(true, false),
    BGRA8_UNORM(true, false),
    BGRA8_UNORM_SRGB(true, false),
    RGBA16_FLOAT(true, false),
    R32_FLOAT(true, false),
    DEPTH24_STENCIL8(false, true),
    DEPTH32_FLOAT(false, true);

    private final boolean color;
    private final boolean depthStencil;

    TextureFormat(boolean color, boolean depthStencil) {
        this.color = color;
        this.depthStencil = depthStencil;
    }

    /**
     * Returns whether this format can be a color target.
     *
     * @return whether this is a color format
     */
    public boolean isColor() {
        return color;
    }

    /** Uncompressed texel size, excluding row padding and provider metadata. Unknown formats fail. */
    public int bytesPerPixel() {
        if (this == UNKNOWN) throw new IllegalStateException("Unknown texture format has no texel size");
        return this == RGBA16_FLOAT ? 8 : 4;
    }

    /** True when sampling decodes RGB and render-target writes encode RGB using sRGB; alpha is unchanged. */
    public boolean isSrgb() { return this == RGBA8_UNORM_SRGB || this == BGRA8_UNORM_SRGB; }

    /**
     * Returns whether this format contains depth and/or stencil data.
     *
     * @return whether this is a depth/stencil format
     */
    public boolean isDepthStencil() {
        return depthStencil;
    }

    /**
     * Returns whether this format contains a stencil component.
     *
     * @return whether stencil is present
     */
    public boolean hasStencil() {
        return this == DEPTH24_STENCIL8;
    }
}
