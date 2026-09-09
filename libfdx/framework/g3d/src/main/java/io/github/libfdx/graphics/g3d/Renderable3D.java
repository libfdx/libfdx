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
    private BoundingBox cullingBounds;
    private final SkinningPalette skinningPalette;

    /**
     * Creates a renderable3 d.
     *
     * @param meshPart the mesh part
     * @param material the material
     * @param worldTransform the borrowed affine local-to-world transform, or
     * null for identity
     * @param bounds the borrowed local-space bounds, or null to use the mesh bounds
     */
    public Renderable3D(MeshPart meshPart, Material material, Matrix4 worldTransform, BoundingBox bounds) {
        this(meshPart, material, worldTransform, bounds, null);
    }

    /**
     * Creates a renderable3 d.
     *
     * @param meshPart the mesh part
     * @param material the material
     * @param worldTransform the borrowed affine local-to-world transform, or
     * null for identity
     * @param bounds the borrowed local-space bounds, or null to use the mesh bounds
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
        this.cullingBounds = skinningPalette == null ? this.bounds : null;
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
     * Returns the borrowed bounds in local mesh space, before
     * {@link #worldTransform()}. The render queue transforms their center for
     * transparent depth ordering. This object does not update bounds itself;
     * DefaultModelInstance updates its prepared skin bounds as the pose changes.
     * Other callers supplying animated bounds keep them in this space.
     *
     * @return the local-space bounds
     */
    public BoundingBox bounds() {
        return bounds;
    }

    /** Borrowed bounds used for optional frustum culling, or null for always visible.
     * Static renderables default to bounds(); skinned ones default to null because bind-pose bounds are unsafe. */
    public BoundingBox cullingBounds() { return cullingBounds; }

    /** Sets borrowed conservative bounds after any skinning/deformation, in local space before worldTransform.
     * The caller keeps mutable bounds current through submission. Null disables culling for this renderable.
     * Shader displacement also requires expanded bounds or disabled culling. Does not change sorting bounds. */
    public Renderable3D cullingBounds(BoundingBox bounds) { cullingBounds=bounds; return this; }

    /**
     * Returns the optional skinning palette.
     *
     * @return the skinning palette, or null when the renderable is not skinned
     */
    public SkinningPalette skinningPalette() {
        return skinningPalette;
    }
}
