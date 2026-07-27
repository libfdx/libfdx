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
