package io.github.libfdx.graphics.g3d;

import io.github.libfdx.core.Disposable;
import io.github.libfdx.graphics.Mesh;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class DefaultModel implements Model {
    private final ArrayList<ModelNode> nodes;
    private final ArrayList<Material> materials;
    private final ArrayList<AnimationClip> animations;
    private final ArrayList<Mesh> meshes;
    private boolean disposed;

    public DefaultModel(List<ModelNode> nodes, List<Material> materials, List<AnimationClip> animations,
            List<Mesh> meshes) {
        this.nodes = copy(nodes);
        this.materials = copy(materials);
        this.animations = copy(animations);
        this.meshes = copy(meshes);
    }

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

    @Override
    public List<ModelNode> nodes() {
        return Collections.unmodifiableList(nodes);
    }

    @Override
    public List<Material> materials() {
        return Collections.unmodifiableList(materials);
    }

    @Override
    public List<AnimationClip> animations() {
        return Collections.unmodifiableList(animations);
    }

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

    @Override
    public boolean isDisposed() {
        return disposed;
    }
}
