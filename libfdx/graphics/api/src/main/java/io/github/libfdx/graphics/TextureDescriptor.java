package io.github.libfdx.graphics;

import io.github.libfdx.core.FdxException;

/**
 * Describes the values used to create or identify a texture.
 *
 * @author xpenatan
 */
public final class TextureDescriptor {
    private String label = "";
    private int width;
    private int height;
    private TextureFormat format = TextureFormat.RGBA8_UNORM;
    private TextureUsage usage = TextureUsage.SAMPLED;
    private TextureWrap wrapS = TextureWrap.CLAMP_TO_EDGE;
    private TextureWrap wrapT = TextureWrap.CLAMP_TO_EDGE;

    /**
     * Creates an RGBA8 texture descriptor.
     *
     * @param label the debug label
     * @param width the width in pixels
     * @param height the height in pixels
     * @return a new texture descriptor
     */
    public static TextureDescriptor rgba8(String label, int width, int height) {
        return new TextureDescriptor()
                .label(label)
                .size(width, height)
                .format(TextureFormat.RGBA8_UNORM)
                .usage(TextureUsage.SAMPLED);
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
     * Sets the label and returns this texture descriptor.
     *
     * @param label the debug label
     * @return this texture descriptor for chaining
     */
    public TextureDescriptor label(String label) {
        this.label = label != null ? label : "";
        return this;
    }

    /**
     * Returns the width.
     *
     * @return the width
     */
    public int width() {
        return width;
    }

    /**
     * Returns the height.
     *
     * @return the height
     */
    public int height() {
        return height;
    }

    /**
     * Sets the size and returns this texture descriptor.
     *
     * @param width the width in pixels
     * @param height the height in pixels
     * @return this texture descriptor for chaining
     */
    public TextureDescriptor size(int width, int height) {
        if (width <= 0 || height <= 0) {
            throw new FdxException("Texture size must be greater than zero");
        }
        this.width = width;
        this.height = height;
        return this;
    }

    /**
     * Returns the format.
     *
     * @return the format
     */
    public TextureFormat format() {
        return format;
    }

    /**
     * Sets the format and returns this texture descriptor.
     *
     * @param format the format
     * @return this texture descriptor for chaining
     */
    public TextureDescriptor format(TextureFormat format) {
        this.format = format != null ? format : TextureFormat.RGBA8_UNORM;
        return this;
    }

    /**
     * Returns the usage.
     *
     * @return the usage
     */
    public TextureUsage usage() {
        return usage;
    }

    /**
     * Sets the usage and returns this texture descriptor.
     *
     * @param usage the usage
     * @return this texture descriptor for chaining
     */
    public TextureDescriptor usage(TextureUsage usage) {
        this.usage = usage != null ? usage : TextureUsage.SAMPLED;
        return this;
    }

    /**
     * Returns the horizontal texture wrap mode.
     *
     * @return the wrap s
     */
    public TextureWrap wrapS() {
        return wrapS;
    }

    /**
     * Returns the vertical texture wrap mode.
     *
     * @return the wrap t
     */
    public TextureWrap wrapT() {
        return wrapT;
    }

    /**
     * Sets the wrap and returns this texture descriptor.
     *
     * @param wrap the wrap
     * @return this texture descriptor for chaining
     */
    public TextureDescriptor wrap(TextureWrap wrap) {
        return wrap(wrap, wrap);
    }

    /**
     * Sets the wrap and returns this texture descriptor.
     *
     * @param wrapS the horizontal wrap mode
     * @param wrapT the vertical wrap mode
     * @return this texture descriptor for chaining
     */
    public TextureDescriptor wrap(TextureWrap wrapS, TextureWrap wrapT) {
        this.wrapS = wrapS != null ? wrapS : TextureWrap.CLAMP_TO_EDGE;
        this.wrapT = wrapT != null ? wrapT : TextureWrap.CLAMP_TO_EDGE;
        return this;
    }
}
