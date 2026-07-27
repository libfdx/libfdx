package io.github.libfdx.graphics;

import io.github.libfdx.graphics.shader.reflection.ShaderSamplerKind;
import io.github.libfdx.core.FdxException;

/**
 * Describes an independently bindable shader sampler.
 */
public final class SamplerDescriptor {
    private String label = "";
    private TextureFilter minFilter = TextureFilter.LINEAR;
    private TextureFilter magFilter = TextureFilter.LINEAR;
    private TextureFilter mipmapFilter = TextureFilter.LINEAR;
    private TextureWrap wrapU = TextureWrap.CLAMP_TO_EDGE;
    private TextureWrap wrapV = TextureWrap.CLAMP_TO_EDGE;
    private TextureWrap wrapW = TextureWrap.CLAMP_TO_EDGE;
    private ShaderSamplerKind kind = ShaderSamplerKind.FILTERING;
    private CompareFunction compareFunction = CompareFunction.LESS_EQUAL;

    public static SamplerDescriptor filtering(String label) {
        return new SamplerDescriptor().label(label);
    }

    public String label() {
        return label;
    }

    public SamplerDescriptor label(String value) {
        label = value != null ? value : "";
        return this;
    }

    public TextureFilter minFilter() {
        return minFilter;
    }

    public TextureFilter magFilter() {
        return magFilter;
    }

    public TextureFilter mipmapFilter() {
        return mipmapFilter;
    }

    public SamplerDescriptor filters(TextureFilter min, TextureFilter mag, TextureFilter mipmap) {
        minFilter = require(min, "minimum");
        magFilter = require(mag, "magnification");
        mipmapFilter = require(mipmap, "mipmap");
        return this;
    }

    public TextureWrap wrapU() {
        return wrapU;
    }

    public TextureWrap wrapV() {
        return wrapV;
    }

    public TextureWrap wrapW() {
        return wrapW;
    }

    public SamplerDescriptor wrap(TextureWrap u, TextureWrap v, TextureWrap w) {
        wrapU = require(u, "U");
        wrapV = require(v, "V");
        wrapW = require(w, "W");
        return this;
    }

    public ShaderSamplerKind kind() {
        return kind;
    }

    public SamplerDescriptor kind(ShaderSamplerKind value) {
        if (value == null || value == ShaderSamplerKind.NONE
                || value == ShaderSamplerKind.UNKNOWN) {
            throw new FdxException("Sampler descriptor kind must be filtering, non-filtering, or comparison");
        }
        kind = value;
        return this;
    }

    /**
     * Returns the comparison function used by comparison samplers.
     *
     * @return comparison function
     */
    public CompareFunction compareFunction() {
        return compareFunction;
    }

    /**
     * Sets the comparison function used when {@link #kind()} is comparison.
     *
     * @param value comparison function
     * @return this descriptor
     */
    public SamplerDescriptor compareFunction(CompareFunction value) {
        compareFunction = require(value, "comparison");
        return this;
    }

    private static <T> T require(T value, String label) {
        if (value == null) {
            throw new FdxException("Sampler " + label + " value cannot be null");
        }
        return value;
    }
}
