package io.github.libfdx.graphics.g3d;

import io.github.libfdx.graphics.camera.Camera;

import io.github.libfdx.graphics.GraphicsContext;
import io.github.libfdx.graphics.RenderPass;
import io.github.libfdx.graphics.RenderPassCompatibility;
import io.github.libfdx.graphics.shader.runtime.ShaderPassId;

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
    private ShaderPassId shaderPassId;

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
        this(graphics, camera, environment, target, pass,
                ShaderPassId.FORWARD);
    }

    /**
     * Creates a render context with an explicit technique pass.
     *
     * @param graphics the graphics context
     * @param camera the camera
     * @param environment the environment
     * @param target the target value
     * @param pass active render pass
     * @param shaderPassId requested shader technique pass
     */
    public RenderContext3D(GraphicsContext graphics, Camera camera,
            Environment3D environment, RenderTarget3D target,
            RenderPass pass, ShaderPassId shaderPassId) {
        this.graphics = graphics;
        this.camera = camera;
        this.environment = environment;
        this.target = target;
        this.pass = pass;
        this.shaderPassId = shaderPassId != null
                ? shaderPassId : ShaderPassId.FORWARD;
    }

    void reset(Camera camera, Environment3D environment, RenderTarget3D target, RenderPass pass) {
        reset(camera, environment, target, pass, ShaderPassId.FORWARD);
    }

    void reset(Camera camera, Environment3D environment,
            RenderTarget3D target, RenderPass pass,
            ShaderPassId shaderPassId) {
        this.camera = camera;
        this.environment = environment;
        this.target = target;
        this.pass = pass;
        this.shaderPassId = shaderPassId != null
                ? shaderPassId : ShaderPassId.FORWARD;
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

    /**
     * Returns the explicitly requested technique pass.
     *
     * @return shader pass ID
     */
    public ShaderPassId shaderPassId() {
        return shaderPassId;
    }

    /**
     * Returns exact compatibility metadata for the active render pass.
     *
     * @return render-pass compatibility
     */
    public RenderPassCompatibility renderPassCompatibility() {
        if (pass == null) {
            throw new io.github.libfdx.core.FdxException(
                    "RenderContext3D has no active render pass");
        }
        return pass.compatibility();
    }
}
