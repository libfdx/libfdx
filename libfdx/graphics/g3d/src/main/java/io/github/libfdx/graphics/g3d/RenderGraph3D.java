package io.github.libfdx.graphics.g3d;

import io.github.libfdx.graphics.Camera;

import io.github.libfdx.core.Disposable;

public interface RenderGraph3D extends Disposable {
    RenderTarget3D target(String name);

    void render(Camera camera, Environment3D environment, Iterable<? extends ModelInstance> instances);
}
