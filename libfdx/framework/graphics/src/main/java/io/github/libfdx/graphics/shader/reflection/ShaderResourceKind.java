package io.github.libfdx.graphics.shader.reflection;

/**
 * Lists complete reflected resource kinds.
 */
public enum ShaderResourceKind {
    UNIFORM_BUFFER,
    STORAGE_BUFFER,
    SAMPLER,
    SAMPLED_TEXTURE,
    MULTISAMPLED_TEXTURE,
    STORAGE_TEXTURE,
    DEPTH_TEXTURE,
    DEPTH_MULTISAMPLED_TEXTURE,
    EXTERNAL_TEXTURE,
    TEXEL_BUFFER,
    INPUT_ATTACHMENT,
    UNKNOWN
}
