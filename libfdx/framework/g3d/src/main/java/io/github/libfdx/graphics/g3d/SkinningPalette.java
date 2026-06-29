package io.github.libfdx.graphics.g3d;

import io.github.libfdx.core.FdxException;
import io.github.libfdx.math.Matrix4;

import java.util.List;

/**
 * Prepares reusable bone matrices for a skin.
 *
 * @author xpenatan
 */
public final class SkinningPalette {
    private final Skin skin;
    private final Bone[] bones;
    private final Matrix4[] boneMatrices;
    private final float[] values;

    /**
     * Creates a skinning palette.
     *
     * @param skin the skin
     */
    public SkinningPalette(Skin skin) {
        if (skin == null || skin.skeleton() == null) {
            throw new FdxException("SkinningPalette skin and skeleton cannot be null");
        }
        this.skin = skin;
        List<Bone> sourceBones = skin.skeleton().bones();
        bones = sourceBones.toArray(new Bone[0]);
        boneMatrices = new Matrix4[bones.length];
        values = new float[bones.length * Matrix4.VALUE_COUNT];
        for (int i = 0; i < bones.length; i++) {
            if (bones[i] == null) {
                throw new FdxException("SkinningPalette bone cannot be null");
            }
            boneMatrices[i] = new Matrix4();
            boneMatrices[i].copyValues(values, i * Matrix4.VALUE_COUNT);
        }
    }

    /**
     * Updates this palette from an animated model instance.
     *
     * @param instance the model instance
     * @return this skinning palette for chaining
     */
    public SkinningPalette update(DefaultModelInstance instance) {
        if (instance == null) {
            throw new FdxException("SkinningPalette instance cannot be null");
        }
        for (int i = 0; i < bones.length; i++) {
            Bone bone = bones[i];
            Matrix4 boneMatrix = boneMatrices[i];
            instance.copyNodeModelTransform(bone.id(), boneMatrix);
            boneMatrix.mul(bone.inverseBindTransform());
            boneMatrix.copyValues(values, i * Matrix4.VALUE_COUNT);
        }
        return this;
    }

    /**
     * Returns the skin.
     *
     * @return the skin
     */
    public Skin skin() {
        return skin;
    }

    /**
     * Returns the bone count.
     *
     * @return the bone count
     */
    public int size() {
        return bones.length;
    }

    /**
     * Returns a bone matrix copy.
     *
     * @param index the bone index
     * @return the bone matrix
     */
    public Matrix4 boneMatrix(int index) {
        return copyBoneMatrix(index, new Matrix4());
    }

    /**
     * Copies a bone matrix.
     *
     * @param index the bone index
     * @param out the output matrix
     * @return the output matrix
     */
    public Matrix4 copyBoneMatrix(int index, Matrix4 out) {
        if (out == null) {
            throw new FdxException("SkinningPalette output matrix cannot be null");
        }
        return out.set(boneMatrices[checkedIndex(index)]);
    }

    /**
     * Returns packed matrix values.
     *
     * @return the packed matrix values
     */
    public float[] values() {
        return values.clone();
    }

    /**
     * Copies packed matrix values.
     *
     * @param out the output values
     * @return the output values
     */
    public float[] copyValues(float[] out) {
        return copyValues(out, 0);
    }

    /**
     * Copies packed matrix values.
     *
     * @param out the output values
     * @param offset the output offset
     * @return the output values
     */
    public float[] copyValues(float[] out, int offset) {
        if (out == null || offset < 0 || out.length - offset < values.length) {
            throw new FdxException("SkinningPalette output requires " + values.length + " values");
        }
        System.arraycopy(values, 0, out, offset, values.length);
        return out;
    }

    private int checkedIndex(int index) {
        if (index < 0 || index >= bones.length) {
            throw new FdxException("SkinningPalette bone index out of range: " + index);
        }
        return index;
    }
}
