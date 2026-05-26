package io.github.libfdx.graphics.g3d;

import io.github.libfdx.graphics.Camera;

import io.github.libfdx.graphics.GraphicsContext;
import io.github.libfdx.graphics.RenderPass;

public final class RenderContext3D {
    private final GraphicsContext graphics;
    private final Camera camera;
    private final Environment3D environment;
    private final RenderTarget3D target;
    private final RenderPass pass;

    public RenderContext3D(GraphicsContext graphics, Camera camera, Environment3D environment,
            RenderTarget3D target, RenderPass pass) {
        this.graphics = graphics;
        this.camera = camera;
        this.environment = environment;
        this.target = target;
        this.pass = pass;
    }

    public GraphicsContext graphics() {
        return graphics;
    }

    public Camera camera() {
        return camera;
    }

    public Environment3D environment() {
        return environment;
    }

    public RenderTarget3D target() {
        return target;
    }

    public RenderPass pass() {
        return pass;
    }
}
