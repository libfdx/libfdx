package io.github.libfdx.graphics.g3d;

import io.github.libfdx.math.Matrix4;

public final class Bone {
    private final String id;
    private final int parentIndex;
    private final Matrix4 inverseBindTransform;

    public Bone(String id, int parentIndex, Matrix4 inverseBindTransform) {
        this.id = id != null ? id : "";
        this.parentIndex = parentIndex;
        this.inverseBindTransform = inverseBindTransform != null ? inverseBindTransform : Matrix4.IDENTITY;
    }

    public String id() {
        return id;
    }

    public int parentIndex() {
        return parentIndex;
    }

    public Matrix4 inverseBindTransform() {
        return inverseBindTransform;
    }
}
