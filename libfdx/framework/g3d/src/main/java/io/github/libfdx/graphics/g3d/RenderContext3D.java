package io.github.libfdx.graphics.g3d;

import io.github.libfdx.graphics.camera.Camera;

import io.github.libfdx.graphics.GraphicsContext;
import io.github.libfdx.graphics.RenderPass;

/**
 * Represents a render context3 d.
 *
 * @author xpenatan
 */
public final class RenderContext3D {
    private final GraphicsContext graphics;
    private Camera camera;
    private Environment3D environment;
    private RenderTarget3D target;
    private RenderPass pass;

    /**
     * Creates a render context3 d.
     *
     * @param graphics the graphics context
     * @param camera the camera
     * @param environment the environment
     * @param target the target value
     * @param pass the pass
     */
    public RenderContext3D(GraphicsContext graphics, Camera camera, Environment3D environment,
            RenderTarget3D target, RenderPass pass) {
        this.graphics = graphics;
        this.camera = camera;
        this.environment = environment;
        this.target = target;
        this.pass = pass;
    }

    void reset(Camera camera, Environment3D environment, RenderTarget3D target, RenderPass pass) {
        this.camera = camera;
        this.environment = environment;
        this.target = target;
        this.pass = pass;
    }

    void clear() {
        reset(null, null, null, null);
    }

    /**
     * Returns the graphics.
     *
     * @return the graphics
     */
    public GraphicsContext graphics() {
        return graphics;
    }

    /**
     * Returns the camera.
     *
     * @return the camera
     */
    public Camera camera() {
        return camera;
    }

    /**
     * Returns the environment.
     *
     * @return the environment
     */
    public Environment3D environment() {
        return environment;
    }

    /**
     * Returns the target.
     *
     * @return the target
     */
    public RenderTarget3D target() {
        return target;
    }

    /**
     * Returns the pass.
     *
     * @return the pass
     */
    public RenderPass pass() {
        return pass;
    }
}
