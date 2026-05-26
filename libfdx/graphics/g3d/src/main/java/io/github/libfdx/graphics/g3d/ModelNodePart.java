package io.github.libfdx.graphics.g3d;

import io.github.libfdx.core.FdxException;

public final class ModelNodePart {
    private final MeshPart meshPart;
    private final Material material;
    private final int[] bones;

    public ModelNodePart(MeshPart meshPart, Material material) {
        this(meshPart, material, null);
    }

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

    public MeshPart meshPart() {
        return meshPart;
    }

    public Material material() {
        return material;
    }

    public int[] bones() {
        return bones.clone();
    }
}
