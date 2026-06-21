package io.github.libfdx.graphics.g3d;

import io.github.libfdx.graphics.camera.Camera;

import io.github.libfdx.core.Disposable;

/**
 * Defines the contract for render path3 d implementations.
 *
 * @author xpenatan
 */
public interface RenderPath3D extends Disposable {
    /**
     * Renders the current content.
     *
     * @param batch the batch
     * @param camera the camera
     * @param environment the environment
     * @param instances the instances
     */
    void render(Batch3D batch, Camera camera, Environment3D environment,
            Iterable<? extends ModelInstance> instances);
}
