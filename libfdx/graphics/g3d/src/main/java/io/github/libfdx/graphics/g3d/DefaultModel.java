package io.github.libfdx.graphics.g3d;

import io.github.libfdx.core.Disposable;
import io.github.libfdx.graphics.Mesh;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Provides the default implementation of a model.
 *
 * @author xpenatan
 */
public final class DefaultModel implements Model {
    private final ArrayList<ModelNode> nodes;
    private final ArrayList<Material> materials;
    private final ArrayList<AnimationClip> animations;
    private final ArrayList<Mesh> meshes;
    private boolean disposed;

    /**
     * Creates a default model.
     *
     * @param nodes the nodes
     * @param materials the materials
     * @param animations the animations
     * @param meshes the meshes
     */
    public DefaultModel(List<ModelNode> nodes, List<Material> materials, List<AnimationClip> animations,
            List<Mesh> meshes) {
        this.nodes = copy(nodes);
        this.materials = copy(materials);
        this.animations = copy(animations);
        this.meshes = copy(meshes);
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
        ModelNodePart nodePart = new ModelNodePart(meshPart, material);
        ModelNode node = new ModelNode(id).addPart(nodePart);
        ArrayList<ModelNode> nodes = new ArrayList<ModelNode>();
        nodes.add(node);
        ArrayList<Material> materials = new ArrayList<Material>();
        materials.add(material);
        ArrayList<Mesh> meshes = new ArrayList<Mesh>();
        meshes.add(meshPart.mesh());
        return new DefaultModel(nodes, materials, Collections.<AnimationClip>emptyList(), meshes);
    }

    private static <T> ArrayList<T> copy(List<T> values) {
        return values != null ? new ArrayList<T>(values) : new ArrayList<T>();
    }

    /**
     * Returns the nodes.
     *
     * @return the nodes
     */
    @Override
    public List<ModelNode> nodes() {
        return Collections.unmodifiableList(nodes);
    }

    /**
     * Returns the materials.
     *
     * @return the materials
     */
    @Override
    public List<Material> materials() {
        return Collections.unmodifiableList(materials);
    }

    /**
     * Returns the animations.
     *
     * @return the animations
     */
    @Override
    public List<AnimationClip> animations() {
        return Collections.unmodifiableList(animations);
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
        Set<Mesh> disposedMeshes = new HashSet<Mesh>();
        for (int i = 0; i < meshes.size(); i++) {
            Mesh mesh = meshes.get(i);
            if (mesh != null && disposedMeshes.add(mesh)) {
                mesh.dispose();
            }
        }
        Set<Disposable> disposedMaterials = new HashSet<Disposable>();
        for (int i = 0; i < materials.size(); i++) {
            Material material = materials.get(i);
            if (material instanceof Disposable && disposedMaterials.add((Disposable)material)) {
                ((Disposable)material).dispose();
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
