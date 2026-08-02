package io.github.libfdx.graphics.g3d;

import io.github.libfdx.collections.Array;
import io.github.libfdx.collections.ArrayView;

/**
 * Represents a skeleton.
 *
 * @author xpenatan
 */
public final class Skeleton {
    private final Array<Bone> bones;
    private final ArrayView<Bone> readOnlyBones;

    /**
     * Creates a skeleton.
     *
     * @param bones the bones
     */
    public Skeleton(ArrayView<Bone> bones) {
        this.bones = bones != null ? new Array<Bone>(bones) : new Array<Bone>(0);
        readOnlyBones = this.bones.view();
    }

    /**
     * Returns the bones.
     *
     * @return the bones
     */
    public ArrayView<Bone> bones() {
        return readOnlyBones;
    }
}
