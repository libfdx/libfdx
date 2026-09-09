package io.github.libfdx.graphics;

/**
 * Provider-neutral graphics features that affect portable resource, pipeline,
 * or command contracts.
 */
public enum GraphicsFeature {
    INDEXED_DRAW,
    INSTANCED_DRAW,
    SEPARATE_SAMPLERS,
    /** Explicit color mip allocation, whole-chain upload and single-level attachment views. */
    TEXTURE_MIP_LEVELS,
    /** Independent minification and magnification filters in TextureDescriptor. */
    TEXTURE_MIN_MAG_FILTERS,
    MULTIPLE_COLOR_ATTACHMENTS,
    DEPTH_STENCIL_ATTACHMENTS,
    EXPLICIT_DEPTH_STENCIL_ATTACHMENTS,
    MULTISAMPLE,
    RESOLVE_ATTACHMENTS,
    ALPHA_BLEND_CONTROL,
    COMPLETE_RENDER_PIPELINE_STATE,
    STORAGE_BUFFERS,
    STORAGE_TEXTURES,
    COMPUTE,
    ATOMICS
}
