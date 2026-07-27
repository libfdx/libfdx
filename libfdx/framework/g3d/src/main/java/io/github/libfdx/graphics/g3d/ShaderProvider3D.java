package io.github.libfdx.graphics.g3d;

import io.github.libfdx.graphics.shader.runtime.ShaderProvider;

/**
 * Defines the contract for shader provider3 d implementations.
 *
 * @author xpenatan
 */
public interface ShaderProvider3D extends ShaderProvider {
    /**
     * Runs the shader step.
     *
     * @param renderable the renderable
     * @param context the context
     * @return the shader
     */
    Shader3D shader(Renderable3D renderable, RenderContext3D context);
}
