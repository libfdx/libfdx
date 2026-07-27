package io.github.libfdx.graphics.shader.runtime;

/**
 * Concrete value kind stored in a shader resource set.
 */
public enum ShaderResourceValueKind {
    PARAMETER_BLOCK,
    BUFFER,
    TEXTURE,
    SAMPLER,
    TEXTURE_SAMPLER
}
