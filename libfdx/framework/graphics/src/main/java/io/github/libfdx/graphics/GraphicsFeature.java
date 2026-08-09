package io.github.libfdx.graphics;

/**
 * Provider-neutral graphics features that affect portable resource, pipeline,
 * or command contracts.
 */
public enum GraphicsFeature {
    INDEXED_DRAW,
    INSTANCED_DRAW,
    SEPARATE_SAMPLERS,
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
