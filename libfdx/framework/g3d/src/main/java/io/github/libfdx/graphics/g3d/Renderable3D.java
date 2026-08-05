package io.github.libfdx.graphics.g3d;

import io.github.libfdx.math.BoundingBox;
import io.github.libfdx.math.Matrix4;

import io.github.libfdx.core.FdxException;

/**
 * Represents a renderable3 d.
 *
 * @author xpenatan
 */
public final class Renderable3D {
    private final MeshPart meshPart;
    private Material material;
    private final Matrix4 worldTransform;
    private final BoundingBox bounds;
    private final SkinningPalette skinningPalette;

    /**
     * Creates a renderable3 d.
     *
     * @param meshPart the mesh part
     * @param material the material
     * @param worldTransform the world transform
     * @param bounds the bounds
     */
    public Renderable3D(MeshPart meshPart, Material material, Matrix4 worldTransform, BoundingBox bounds) {
        this(meshPart, material, worldTransform, bounds, null);
    }

    /**
     * Creates a renderable3 d.
     *
     * @param meshPart the mesh part
     * @param material the material
     * @param worldTransform the world transform
     * @param bounds the bounds
     * @param skinningPalette the optional skinning palette
     */
    public Renderable3D(MeshPart meshPart, Material material, Matrix4 worldTransform, BoundingBox bounds,
            SkinningPalette skinningPalette) {
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
        this.skinningPalette = skinningPalette;
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

    void material(Material material) {
        if (material == null) {
            throw new FdxException("Renderable3D material cannot be null");
        }
        this.material = material;
    }

    /**
     * Returns the world transform.
     *
     * @return the world transform
     */
    public Matrix4 worldTransform() {
        return worldTransform;
    }

    /**
     * Returns the bounds.
     *
     * @return the bounds
     */
    public BoundingBox bounds() {
        return bounds;
    }

    /**
     * Returns the optional skinning palette.
     *
     * @return the skinning palette, or null when the renderable is not skinned
     */
    public SkinningPalette skinningPalette() {
        return skinningPalette;
    }
}
