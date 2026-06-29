package io.github.libfdx.graphics.g3d;

import io.github.libfdx.math.Matrix4;

/**
 * Defines the contract for model instance implementations.
 *
 * @author xpenatan
 */
public interface ModelInstance {
    /**
     * Returns the model.
     *
     * @return the model
     */
    Model model();

    /**
     * Returns the transform.
     *
     * @return the transform
     */
    Matrix4 transform();

    /**
     * Runs the collect renderables step.
     *
     * @param queue the queue
     */
    void collectRenderables(RenderQueue3D queue);
}
