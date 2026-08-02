package io.github.libfdx.graphics.g3d;

import io.github.libfdx.collections.ObjectIterable;
import io.github.libfdx.graphics.camera.Camera;

import io.github.libfdx.core.Disposable;

/**
 * Defines the contract for render graph3 d implementations.
 *
 * @author xpenatan
 */
public interface RenderGraph3D extends Disposable {
    /**
     * Runs the target step.
     *
     * @param name the name
     * @return the target
     */
    RenderTarget3D target(String name);

    /**
     * Renders the current content.
     *
     * @param camera the camera
     * @param environment the environment
     * @param instances the instances
     */
    void render(Camera camera, Environment3D environment,
            ObjectIterable<? extends ModelInstance> instances);
}
