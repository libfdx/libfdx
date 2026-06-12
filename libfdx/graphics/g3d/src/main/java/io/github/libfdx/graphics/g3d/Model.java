package io.github.libfdx.graphics.g3d;

import io.github.libfdx.core.Disposable;

import java.util.List;

/**
 * Defines the contract for model implementations.
 *
 * @author xpenatan
 */
public interface Model extends Disposable {
    /**
     * Returns the nodes.
     *
     * @return the nodes
     */
    List<ModelNode> nodes();

    /**
     * Returns the materials.
     *
     * @return the materials
     */
    List<Material> materials();

    /**
     * Returns the animations.
     *
     * @return the animations
     */
    List<AnimationClip> animations();
}
