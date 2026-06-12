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
        if (meshPart == null) {
            throw new FdxException("ModelNodePart mesh part cannot be null");
        }
        if (material == null) {
            throw new FdxException("ModelNodePart material cannot be null");
        }
        this.meshPart = meshPart;
        this.material = material;
        this.bones = bones != null ? bones.clone() : new int[0];
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
}
