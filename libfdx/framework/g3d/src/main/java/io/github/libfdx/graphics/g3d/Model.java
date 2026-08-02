package io.github.libfdx.graphics.g3d;

import io.github.libfdx.collections.ArrayView;
import io.github.libfdx.core.Disposable;

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
    ArrayView<ModelNode> nodes();

    /**
     * Returns the materials.
     *
     * @return the materials
     */
    ArrayView<Material> materials();

    /**
     * Returns the animations.
     *
     * @return the animations
     */
    ArrayView<AnimationClip> animations();

    /**
     * Returns the skins.
     *
     * @return the skins
     */
    ArrayView<Skin> skins();
}
