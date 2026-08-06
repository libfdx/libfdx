package io.github.libfdx.graphics.g3d;

import io.github.libfdx.collections.Array;
import io.github.libfdx.collections.ArrayView;
import io.github.libfdx.collections.ObjectSet;
import io.github.libfdx.core.Disposable;
import io.github.libfdx.graphics.Mesh;

/**
 * Provides the default implementation of a model.
 *
 * @author xpenatan
 */
public final class DefaultModel implements Model {
    private final Array<ModelNode> nodes;
    private final Array<Material> materials;
    private final Array<AnimationClip> animations;
    private final Array<Skin> skins;
    private final Array<Mesh> meshes;
    private final Array<Disposable> ownedResources;
    private final ArrayView<ModelNode> readOnlyNodes;
    private final ArrayView<Material> readOnlyMaterials;
    private final ArrayView<AnimationClip> readOnlyAnimations;
    private final ArrayView<Skin> readOnlySkins;
    private boolean disposed;

    /**
     * Creates a default model.
     *
     * @param nodes the nodes
     * @param materials the materials
     * @param animations the animations
     * @param meshes the meshes
     */
    public DefaultModel(ArrayView<ModelNode> nodes, ArrayView<Material> materials,
            ArrayView<AnimationClip> animations, ArrayView<Mesh> meshes) {
        this(nodes, materials, animations, new Array<Skin>(0), meshes, null);
    }

    /**
     * Creates a default model.
     *
     * @param nodes the nodes
     * @param materials the materials
     * @param animations the animations
     * @param skins the skins
     * @param meshes the meshes
     */
    public DefaultModel(ArrayView<ModelNode> nodes, ArrayView<Material> materials,
            ArrayView<AnimationClip> animations, ArrayView<Skin> skins, ArrayView<Mesh> meshes) {
        this(nodes, materials, animations, skins, meshes, null);
    }

    /**
     * Creates a default model with additional explicitly owned resources.
     * Materials and their attributes are borrowed.
     *
     * @param nodes the nodes
     * @param materials borrowed materials
     * @param animations the animations
     * @param skins the skins
     * @param meshes owned meshes
     * @param ownedResources additional owned resources
     */
    public DefaultModel(ArrayView<ModelNode> nodes,
            ArrayView<Material> materials,
            ArrayView<AnimationClip> animations, ArrayView<Skin> skins,
            ArrayView<Mesh> meshes,
            ArrayView<? extends Disposable> ownedResources) {
        this.nodes = copy(nodes);
        this.materials = copy(materials);
        this.animations = copy(animations);
        this.skins = copy(skins);
        this.meshes = copy(meshes);
        this.ownedResources = new Array<Disposable>();
        if (ownedResources != null) {
            for (int i = 0; i < ownedResources.size(); i++) {
                Disposable resource = ownedResources.get(i);
                if (resource != null) {
                    this.ownedResources.add(resource);
                }
            }
        }
        readOnlyNodes = this.nodes.view();
        readOnlyMaterials = this.materials.view();
        readOnlyAnimations = this.animations.view();
        readOnlySkins = this.skins.view();
    }

    /**
     * Creates a default model.
     *
     * @param id the identifier
     * @param meshPart the mesh part
     * @param material the material
     * @return a new default model
     */
    public static DefaultModel singleNode(String id, MeshPart meshPart, Material material) {
        return singleNode(id, meshPart, material, (Disposable[])null);
    }

    /**
     * Creates a one-node model with additional explicitly owned resources.
     *
     * @param id model ID
     * @param meshPart owned mesh part
     * @param material borrowed material
     * @param ownedResources additional owned resources
     * @return model
     */
    public static DefaultModel singleNode(String id, MeshPart meshPart,
            Material material, Disposable... ownedResources) {
        ModelNodePart nodePart = new ModelNodePart(meshPart, material);
        ModelNode node = new ModelNode(id).addPart(nodePart);
        Array<ModelNode> nodes = new Array<ModelNode>();
        nodes.add(node);
        Array<Material> materials = new Array<Material>();
        materials.add(material);
        Array<Mesh> meshes = new Array<Mesh>();
        meshes.add(meshPart.mesh());
        Array<Disposable> resources = new Array<Disposable>();
        if (ownedResources != null) {
            for (Disposable resource : ownedResources) {
                if (resource != null) {
                    resources.add(resource);
                }
            }
        }
        return new DefaultModel(nodes, materials,
                new Array<AnimationClip>(0), new Array<Skin>(0), meshes,
                resources);
    }

    private static <T> Array<T> copy(ArrayView<T> values) {
        return values != null ? new Array<T>(values) : new Array<T>(0);
    }

    /**
     * Returns the nodes.
     *
     * @return the nodes
     */
    @Override
    public ArrayView<ModelNode> nodes() {
        return readOnlyNodes;
    }

    /**
     * Returns the materials.
     *
     * @return the materials
     */
    @Override
    public ArrayView<Material> materials() {
        return readOnlyMaterials;
    }

    /**
     * Returns the animations.
     *
     * @return the animations
     */
    @Override
    public ArrayView<AnimationClip> animations() {
        return readOnlyAnimations;
    }

    /**
     * Returns the skins.
     *
     * @return the skins
     */
    @Override
    public ArrayView<Skin> skins() {
        return readOnlySkins;
    }

    /**
     * Releases resources held by this instance.
     */
    @Override
    public void dispose() {
        if (disposed) {
            return;
        }
        disposed = true;
        ObjectSet<Disposable> disposedResources =
                new ObjectSet<Disposable>();
        for (int i = 0; i < meshes.size(); i++) {
            Mesh mesh = meshes.get(i);
            if (mesh != null && disposedResources.add(mesh)) {
                mesh.dispose();
            }
        }
        for (int i = 0; i < ownedResources.size(); i++) {
            Disposable resource = ownedResources.get(i);
            if (resource != null && disposedResources.add(resource)) {
                resource.dispose();
            }
        }
    }

    /**
     * Returns whether this instance has already been disposed.
     *
     * @return true if disposed is enabled or true; false otherwise
     */
    @Override
    public boolean isDisposed() {
        return disposed;
    }
}
