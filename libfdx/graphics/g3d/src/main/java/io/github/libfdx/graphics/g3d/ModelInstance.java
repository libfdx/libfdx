package io.github.libfdx.graphics.g3d;

import io.github.libfdx.math.Matrix4;

public interface ModelInstance {
    Model model();

    Matrix4 transform();

    void collectRenderables(RenderQueue3D queue);
}
