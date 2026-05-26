package io.github.libfdx.graphics.g3d;

import io.github.libfdx.math.BoundingBox;
import io.github.libfdx.math.Matrix4;

import io.github.libfdx.core.FdxException;

public final class Renderable3D {
    private final MeshPart meshPart;
    private final Material material;
    private final Matrix4 worldTransform;
    private final BoundingBox bounds;

    public Renderable3D(MeshPart meshPart, Material material, Matrix4 worldTransform, BoundingBox bounds) {
        if (meshPart == null) {
            throw new FdxException("Renderable3D mesh part cannot be null");
        }
        if (material == null) {
            throw new FdxException("Renderable3D material cannot be null");
        }
        this.meshPart = meshPart;
        this.material = material;
        this.worldTransform = worldTransform != null ? worldTransform : Matrix4.IDENTITY;
        this.bounds = bounds != null ? bounds : meshPart.mesh().bounds();
    }

    public MeshPart meshPart() {
        return meshPart;
    }

    public Material material() {
        return material;
    }

    public Matrix4 worldTransform() {
        return worldTransform;
    }

    public BoundingBox bounds() {
        return bounds;
    }
}
