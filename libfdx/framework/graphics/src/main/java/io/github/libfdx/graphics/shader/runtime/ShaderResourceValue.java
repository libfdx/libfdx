package io.github.libfdx.graphics.shader.runtime;

import io.github.libfdx.graphics.Buffer;
import io.github.libfdx.graphics.Sampler;
import io.github.libfdx.graphics.Texture;

/**
 * Immutable borrowed value for one shader resource binding.
 */
public final class ShaderResourceValue {
    private final int binding;
    private final ShaderResourceValueKind kind;
    private final ShaderParameterBlock parameterBlock;
    private final Buffer buffer;
    private final Texture texture;
    private final Sampler sampler;
    private final int offset;
    private final int size;

    ShaderResourceValue(int binding, ShaderResourceValueKind kind,
            ShaderParameterBlock parameterBlock, Buffer buffer,
            Texture texture, Sampler sampler, int offset, int size) {
        this.binding = binding;
        this.kind = kind;
        this.parameterBlock = parameterBlock;
        this.buffer = buffer;
        this.texture = texture;
        this.sampler = sampler;
        this.offset = offset;
        this.size = size;
    }

    public int binding() {
        return binding;
    }

    public ShaderResourceValueKind kind() {
        return kind;
    }

    public ShaderParameterBlock parameterBlock() {
        return parameterBlock;
    }

    public Buffer buffer() {
        return buffer;
    }

    public Texture texture() {
        return texture;
    }

    public Sampler sampler() {
        return sampler;
    }

    public int offset() {
        return offset;
    }

    public int size() {
        return size;
    }
}
