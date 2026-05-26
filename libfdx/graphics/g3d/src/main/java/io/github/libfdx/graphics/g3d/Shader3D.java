package io.github.libfdx.graphics.g3d;

import io.github.libfdx.core.Disposable;

public interface Shader3D extends Disposable {
    boolean canRender(Renderable3D renderable);

    void begin(RenderContext3D context);

    void render(Renderable3D renderable);

    void end();
}
