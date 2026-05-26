package io.github.libfdx.graphics.g3d;

import io.github.libfdx.graphics.Camera;

import java.util.List;

public interface RenderQueue3D {
    void clear();

    void add(Renderable3D renderable);

    int size();

    Renderable3D get(int index);

    void sort(Camera camera);

    List<Renderable3D> renderables();
}
