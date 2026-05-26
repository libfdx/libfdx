package io.github.libfdx.graphics.g3d;

import io.github.libfdx.core.Disposable;

import java.util.List;

public interface Model extends Disposable {
    List<ModelNode> nodes();

    List<Material> materials();

    List<AnimationClip> animations();
}
