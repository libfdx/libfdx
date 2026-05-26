package io.github.libfdx.graphics.g3d;

public interface ShaderProvider3D {
    Shader3D shader(Renderable3D renderable, RenderContext3D context);
}
