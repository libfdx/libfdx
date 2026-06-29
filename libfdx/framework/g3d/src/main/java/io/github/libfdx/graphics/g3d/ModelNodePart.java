package io.github.libfdx.graphics.g3d;

import io.github.libfdx.core.FdxException;

/**
 * Represents a model node part.
 *
 * @author xpenatan
 */
public final class ModelNodePart {
    private final MeshPart meshPart;
    private final Material material;
    private final int[] bones;
    private final Skin skin;
    private final int[] joints;
    private final float[] weights;

    /**
     * Creates a model node part.
     *
     * @param meshPart the mesh part
     * @param material the material
     */
    public ModelNodePart(MeshPart meshPart, Material material) {
        this(meshPart, material, null);
    }

    /**
     * Creates a model node part.
     *
     * @param meshPart the mesh part
     * @param material the material
     * @param bones the bones
     */
    public ModelNodePart(MeshPart meshPart, Material material, int[] bones) {
        this(meshPart, material, null, bones, null);
    }

    /**
     * Creates a model node part.
     *
     * @param meshPart the mesh part
     * @param material the material
     * @param skin the skin
     * @param joints four joint indices per vertex
     * @param weights four joint weights per vertex
     */
    public ModelNodePart(MeshPart meshPart, Material material, Skin skin, int[] joints, float[] weights) {
        if (meshPart == null) {
            throw new FdxException("ModelNodePart mesh part cannot be null");
        }
        if (material == null) {
            throw new FdxException("ModelNodePart material cannot be null");
        }
        this.meshPart = meshPart;
        this.material = material;
        this.skin = skin;
        this.joints = joints != null ? joints.clone() : new int[0];
        this.weights = weights != null ? weights.clone() : new float[0];
        this.bones = this.joints;
    }

    /**
     * Returns the mesh part.
     *
     * @return the mesh part
     */
    public MeshPart meshPart() {
        return meshPart;
    }

    /**
     * Returns the material.
     *
     * @return the material
     */
    public Material material() {
        return material;
    }

    /**
     * Returns the bones.
     *
     * @return the bones
     */
    public int[] bones() {
        return bones.clone();
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
     * Returns the joints.
     *
     * @return the joints
     */
    public int[] joints() {
        return joints.clone();
    }

    /**
     * Returns the weights.
     *
     * @return the weights
     */
    public float[] weights() {
        return weights.clone();
    }
}
