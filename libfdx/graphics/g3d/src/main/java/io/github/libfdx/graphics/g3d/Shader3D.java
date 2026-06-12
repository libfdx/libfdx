package io.github.libfdx.graphics.g3d;

import io.github.libfdx.core.Disposable;

/**
 * Defines the contract for shader3 d implementations.
 *
 * @author xpenatan
 */
public interface Shader3D extends Disposable {
    /**
     * Returns whether this instance can render.
     *
     * @param renderable the renderable
     * @return true if can render succeeds or is active; false otherwise
     */
    boolean canRender(Renderable3D renderable);

    /**
     * Begins the operation.
     *
     * @param context the context
     */
    void begin(RenderContext3D context);

    /**
     * Renders the current content.
     *
     * @param renderable the renderable
     */
    void render(Renderable3D renderable);

    /**
     * Ends the operation.
     */
    void end();
}
