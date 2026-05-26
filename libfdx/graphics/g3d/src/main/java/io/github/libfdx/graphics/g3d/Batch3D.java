package io.github.libfdx.graphics.g3d;

import io.github.libfdx.graphics.Camera;

import io.github.libfdx.core.Disposable;
import io.github.libfdx.graphics.LoadOp;
import io.github.libfdx.graphics.RenderPass;

public interface Batch3D extends Disposable {
    void begin(Camera camera);

    void begin(LoadOp loadOp, Camera camera);

    void begin(RenderPass pass, Camera camera);

    void begin(RenderTarget3D target, Camera camera);

    Batch3D environment(Environment3D environment);

    Batch3D shaderProvider(ShaderProvider3D shaderProvider);

    void render(ModelInstance instance);

    void render(Renderable3D renderable);

    void render(Iterable<? extends ModelInstance> instances);

    void flush();

    void end();
}
