package io.github.libfdx.graphics.g3d;

import io.github.libfdx.graphics.camera.Camera;

import io.github.libfdx.core.Disposable;
import io.github.libfdx.graphics.LoadOp;
import io.github.libfdx.graphics.RenderPass;

/**
 * Defines the contract for batch3 d implementations.
 *
 * @author xpenatan
 */
public interface Batch3D extends Disposable {
    /**
     * Begins the operation.
     *
     * @param camera the camera
     */
    void begin(Camera camera);

    /**
     * Begins the operation.
     *
     * @param loadOp the load op
     * @param camera the camera
     */
    void begin(LoadOp loadOp, Camera camera);

    /**
     * Begins the operation.
     *
     * @param pass the pass
     * @param camera the camera
     */
    void begin(RenderPass pass, Camera camera);

    /**
     * Begins the operation.
     *
     * @param target the target value
     * @param camera the camera
     */
    void begin(RenderTarget3D target, Camera camera);

    /**
     * Sets the environment and returns this batch3 d.
     *
     * @param environment the environment
     * @return this batch3 d for chaining
     */
    Batch3D environment(Environment3D environment);

    /**
     * Sets the shader provider and returns this batch3 d.
     *
     * @param shaderProvider the shader provider
     * @return this batch3 d for chaining
     */
    Batch3D shaderProvider(ShaderProvider3D shaderProvider);

    /**
     * Renders the current content.
     *
     * @param instance the instance
     */
    void render(ModelInstance instance);

    /**
     * Renders the current content.
     *
     * @param renderable the renderable
     */
    void render(Renderable3D renderable);

    /**
     * Renders the current content.
     *
     * @param instances the instances
     */
    void render(Iterable<? extends ModelInstance> instances);

    /**
     * Runs the flush step.
     */
    void flush();

    /**
     * Ends the operation.
     */
    void end();
}
