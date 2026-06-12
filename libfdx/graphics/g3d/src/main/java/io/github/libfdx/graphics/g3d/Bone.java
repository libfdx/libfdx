package io.github.libfdx.graphics.g3d;

import io.github.libfdx.math.Matrix4;

/**
 * Represents a bone.
 *
 * @author xpenatan
 */
public final class Bone {
    private final String id;
    private final int parentIndex;
    private final Matrix4 inverseBindTransform;

    /**
     * Creates a bone.
     *
     * @param id the identifier
     * @param parentIndex the parent index
     * @param inverseBindTransform the inverse bind transform
     */
    public Bone(String id, int parentIndex, Matrix4 inverseBindTransform) {
        this.id = id != null ? id : "";
        this.parentIndex = parentIndex;
        this.inverseBindTransform = inverseBindTransform != null ? inverseBindTransform : Matrix4.IDENTITY;
    }

    /**
     * Returns the ID.
     *
     * @return the ID
     */
    public String id() {
        return id;
    }

    /**
     * Returns the parent index.
     *
     * @return the parent index
     */
    public int parentIndex() {
        return parentIndex;
    }

    /**
     * Returns the inverse bind transform.
     *
     * @return the inverse bind transform
     */
    public Matrix4 inverseBindTransform() {
        return inverseBindTransform;
    }
}
