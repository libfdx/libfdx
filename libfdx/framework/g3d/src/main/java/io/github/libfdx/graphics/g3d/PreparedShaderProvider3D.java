package io.github.libfdx.graphics.g3d;

/**
 * Explicit capability for a model renderer that consumes already prepared passes. Its shader
 * selection, begin and render methods must use {@link RenderContext3D#preparedShaderPass()} and
 * must never generate/compile shaders or create pipelines. ModelBatch skips unavailable passes
 * before invoking those methods. The plan is borrowed and must also be used during preloading.
 */
public interface PreparedShaderProvider3D extends ShaderProvider3D {
    /** Returns the shared definitions, or null when this instance is configured for synchronous rendering. */
    ModelShaderPlan preparationPlan();
}
