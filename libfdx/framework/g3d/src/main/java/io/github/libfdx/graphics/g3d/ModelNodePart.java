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
    private final SkinnedBounds3D skinBounds;
    private final MorphTarget[] morphTargets;

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
     * @param joints copied four joint indices per vertex, matching mesh attributes; null uses retained mesh influences
     * @param weights copied finite nonnegative weights per vertex, matching mesh attributes; null uses retained mesh influences
     */
    public ModelNodePart(MeshPart meshPart, Material material, Skin skin, int[] joints, float[] weights) {
        this(meshPart, material, skin, joints, weights, null);
    }

    /** Copies the target array; immutable target deltas match the complete mesh vertex domain. */
    public ModelNodePart(MeshPart meshPart, Material material, Skin skin, int[] joints, float[] weights, MorphTarget[] morphTargets) {
        if (meshPart == null) {
            throw new FdxException("ModelNodePart mesh part cannot be null");
        }
        if (material == null) {
            throw new FdxException("ModelNodePart material cannot be null");
        }
        this.meshPart = meshPart;
        this.material = material;
        this.skin = skin;
        this.morphTargets = morphTargets == null ? new MorphTarget[0] : morphTargets.clone();
        for (MorphTarget target : this.morphTargets)
            if (target == null || target.vertexCount() != 0 && target.vertexCount() != meshPart.mesh().vertexCount())
                throw new FdxException("Morph targets must match mesh vertices");
        int[] meshJoints=meshPart.mesh().sourceJoints();
        float[] meshWeights=meshPart.mesh().sourceWeights();
        if (skin != null && meshJoints != null && joints != null && !java.util.Arrays.equals(meshJoints,joints)
                || skin != null && meshWeights != null && weights != null && !java.util.Arrays.equals(meshWeights,weights))
            throw new FdxException("Model node skin influences must match the mesh vertex attributes");
        int[] selectedJoints=joints != null ? joints : skin != null ? meshJoints : null;
        float[] selectedWeights=weights != null ? weights : skin != null ? meshWeights : null;
        this.joints = selectedJoints != null ? selectedJoints.clone() : new int[0];
        this.weights = selectedWeights != null ? selectedWeights.clone() : new float[0];
        this.bones = this.joints;
        float[] positions=meshPart.mesh().sourcePositions();
        skinBounds=skin != null && positions != null && this.joints.length != 0 && this.weights.length != 0
                ? new SkinnedBounds3D(positions,this.joints,this.weights,skin.skeleton().bones().size()) : null;
    }

    SkinnedBounds3D skinBounds() { return skinBounds; }
    public int morphTargetCount() { return morphTargets.length; }
    public MorphTarget morphTarget(int index) { return morphTargets[index]; }
    public MorphTarget[] morphTargets() { return morphTargets.clone(); }

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
