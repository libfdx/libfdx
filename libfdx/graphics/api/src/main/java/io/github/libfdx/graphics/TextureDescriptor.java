package io.github.libfdx.graphics;

import io.github.libfdx.core.FdxException;

public final class TextureDescriptor {
    private String label = "";
    private int width;
    private int height;
    private TextureFormat format = TextureFormat.RGBA8_UNORM;
    private TextureUsage usage = TextureUsage.SAMPLED;
    private TextureWrap wrapS = TextureWrap.CLAMP_TO_EDGE;
    private TextureWrap wrapT = TextureWrap.CLAMP_TO_EDGE;

    public static TextureDescriptor rgba8(String label, int width, int height) {
        return new TextureDescriptor()
                .label(label)
                .size(width, height)
                .format(TextureFormat.RGBA8_UNORM)
                .usage(TextureUsage.SAMPLED);
    }

    public String label() {
        return label;
    }

    public TextureDescriptor label(String label) {
        this.label = label != null ? label : "";
        return this;
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public TextureDescriptor size(int width, int height) {
        if (width <= 0 || height <= 0) {
            throw new FdxException("Texture size must be greater than zero");
        }
        this.width = width;
        this.height = height;
        return this;
    }

    public TextureFormat format() {
        return format;
    }

    public TextureDescriptor format(TextureFormat format) {
        this.format = format != null ? format : TextureFormat.RGBA8_UNORM;
        return this;
    }

    public TextureUsage usage() {
        return usage;
    }

    public TextureDescriptor usage(TextureUsage usage) {
        this.usage = usage != null ? usage : TextureUsage.SAMPLED;
        return this;
    }

    public TextureWrap wrapS() {
        return wrapS;
    }

    public TextureWrap wrapT() {
        return wrapT;
    }

    public TextureDescriptor wrap(TextureWrap wrap) {
        return wrap(wrap, wrap);
    }

    public TextureDescriptor wrap(TextureWrap wrapS, TextureWrap wrapT) {
        this.wrapS = wrapS != null ? wrapS : TextureWrap.CLAMP_TO_EDGE;
        this.wrapT = wrapT != null ? wrapT : TextureWrap.CLAMP_TO_EDGE;
        return this;
    }
}
