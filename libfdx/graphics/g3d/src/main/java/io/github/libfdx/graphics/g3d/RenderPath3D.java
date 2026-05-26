package io.github.libfdx.graphics.g3d;

import io.github.libfdx.graphics.Camera;

import io.github.libfdx.core.Disposable;

public interface RenderPath3D extends Disposable {
    void render(Batch3D batch, Camera camera, Environment3D environment,
            Iterable<? extends ModelInstance> instances);
}
