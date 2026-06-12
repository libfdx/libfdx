package io.github.libfdx.graphics.g3d;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Represents a skeleton.
 *
 * @author xpenatan
 */
public final class Skeleton {
    private final ArrayList<Bone> bones;

    /**
     * Creates a skeleton.
     *
     * @param bones the bones
     */
    public Skeleton(List<Bone> bones) {
        this.bones = bones != null ? new ArrayList<Bone>(bones) : new ArrayList<Bone>();
    }

    /**
     * Returns the bones.
     *
     * @return the bones
     */
    public List<Bone> bones() {
        return Collections.unmodifiableList(bones);
    }
}
