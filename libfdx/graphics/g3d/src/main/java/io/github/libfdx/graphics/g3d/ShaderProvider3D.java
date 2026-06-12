package io.github.libfdx.graphics.g3d;

/**
 * Defines the contract for shader provider3 d implementations.
 *
 * @author xpenatan
 */
public interface ShaderProvider3D {
    /**
     * Runs the shader step.
     *
     * @param renderable the renderable
     * @param context the context
     * @return the shader
     */
    Shader3D shader(Renderable3D renderable, RenderContext3D context);
}
