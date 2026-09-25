package io.github.libfdx.graphics.meshoptimizer;

import io.github.libfdx.collections.ArrayView;
import io.github.libfdx.graphics.Mesh;
import io.github.libfdx.graphics.PrimitiveTopology;
import io.github.libfdx.graphics.g3d.*;
import io.github.libfdx.math.Matrix4;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.IdentityHashMap;

/**
 * CPU geometry/hierarchy/deformation snapshot of a loaded model. Capture on its owning thread while
 * unmodified; subsequent generation reads the snapshot. Unchanged meshes and materials/textures are borrowed, so keep
 * the original model/asset scope alive until all generated models are disposed.
 */
public final class ModelLodInput {
    final Model source;
    final LodNodeSnapshot[] roots;
    final ArrayList<MeshLodData> geometry = new ArrayList<>();
    final ArrayList<Material> materials = new ArrayList<>();
    final ArrayList<Integer> occurrences = new ArrayList<>();
    final ArrayList<Integer> sourceVertexCounts = new ArrayList<>();
    final ArrayList<MeshPart> sourceParts = new ArrayList<>();
    final ArrayList<int[]> rigidGroups = new ArrayList<>();
    final ArrayList<AnimationClip> animations = new ArrayList<>();
    final ArrayList<Skin> skins = new ArrayList<>();
    final ArrayList<ModelNodePart> sourceNodeParts = new ArrayList<>();
    private final IdentityHashMap<Skin,Skin> copiedSkins = new IdentityHashMap<>();
    private final LodAnimationSamples sampling;
    private final IdentityHashMap<MeshPart,Integer> parts = new IdentityHashMap<>();
    private final IdentityHashMap<ModelNode,Boolean> visited = new IdentityHashMap<>();
    private final HashSet<String> names = new HashSet<>();
    private int triangles, vertices;

    /** Worker-only input: geometry and integer metadata, with no live model/GPU references. */
    ModelLodInput() {
        source = null;
        roots = new LodNodeSnapshot[0];
        sampling = null;
    }

    ModelLodInput detached() {
        ModelLodInput result = new ModelLodInput();
        result.geometry.addAll(geometry);
        result.occurrences.addAll(occurrences);
        result.sourceVertexCounts.addAll(sourceVertexCounts);
        result.rigidGroups.addAll(rigidGroups);
        result.triangles = triangles;
        result.vertices = vertices;
        return result;
    }

    void countGeometry() {
        for(int i = 0; i < geometry.size(); i++) {
            triangles = Math.addExact(triangles, Math.multiplyExact(geometry.get(i).triangleCount(), occurrences.get(i)));
            vertices = Math.addExact(vertices, Math.multiplyExact(geometry.get(i).vertexCount(), occurrences.get(i)));
        }
    }

    private ModelLodInput(Model source,ModelLodAnimationSettings animationSettings) {
        if (source == null || source.isDisposed()) throw new IllegalArgumentException("A live source model is required");
        this.source = source;
        sampling=new LodAnimationSamples(source,java.util.Objects.requireNonNull(animationSettings));
        for (int i=0;i<source.animations().size();i++) animations.add(source.animations().get(i));
        roots = captureNodes(source.nodes());
        if (geometry.isEmpty()) throw new IllegalArgumentException("The model has no triangle geometry");
        parts.clear(); visited.clear(); names.clear();
    }
    public static ModelLodInput capture(Model source) { return capture(source,ModelLodAnimationSettings.balanced()); }
    /** Capture and deformation sampling are explicit synchronous loading work on the model owner.
     * Configure coverage/memory before capture; later preparation can run cooperatively or on a worker. */
    public static ModelLodInput capture(Model source,ModelLodAnimationSettings animationSettings) { return new ModelLodInput(source,animationSettings); }
    public int maxDeformationSamples() { int count=0;for (MeshLodData mesh:geometry) if (mesh.deformation()!=null) count=Math.max(count,mesh.deformation().poseCount());return count; }
    public int triangleCount() { return triangles; }
    public int vertexCount() { return vertices; }
    public int meshCount() { return geometry.size(); }

    private LodNodeSnapshot[] captureNodes(ArrayView<ModelNode> nodes) {
        LodNodeSnapshot[] result = new LodNodeSnapshot[nodes.size()];
        for (int n = 0; n < result.length; n++) {
            ModelNode node = nodes.get(n);
            if (visited.put(node,Boolean.TRUE) != null || node.id().trim().isEmpty() || !names.add(node.id().trim()))
                throw new IllegalArgumentException("LOD models require a tree with unique, nonempty node names");
            LodPartSnapshot[] nodeParts = new LodPartSnapshot[node.parts().size()];
            for (int p = 0; p < nodeParts.length; p++) {
                ModelNodePart part = node.parts().get(p);
                if (part.skin()==null && part.bones().length != 0) throw new IllegalArgumentException("LOD joint influences require a skin mapping");
                MeshPart mesh = part.meshPart();
                Integer index = part.skin()!=null || part.morphTargetCount()>0 ? null : parts.get(mesh);
                if (index == null) {
                    index = geometry.size(); geometry.add(captureMesh(part,sampling.capture(node,part))); occurrences.add(0);
                    sourceParts.add(mesh); sourceNodeParts.add(part); sourceVertexCounts.add(mesh.vertexCount());
                    if (part.skin()==null && part.morphTargetCount()==0) parts.put(mesh,index);
                }
                occurrences.set(index,occurrences.get(index) + 1);
                triangles = Math.addExact(triangles,geometry.get(index).triangleCount());
                vertices = Math.addExact(vertices,geometry.get(index).vertexCount());
                if (!materials.contains(part.material())) materials.add(part.material());
                nodeParts[p] = new LodPartSnapshot(mesh.id(),index,part.material(),copySkin(part.skin()),part.morphTargets());
            }
            if(nodeParts.length>1) {
                int[] group=new int[nodeParts.length];
                for(int p=0;p<group.length;p++) group[p]=nodeParts[p].geometry();
                rigidGroups.add(group);
            }
            result[n] = new LodNodeSnapshot(node.id(),new Matrix4().set(node.localTransform()),nodeParts,captureNodes(node.children()),node.morphWeights());
        }
        return result;
    }

    private static MeshLodData captureMesh(ModelNodePart nodePart,MeshLodDeformation deformation) {
        MeshPart part=nodePart.meshPart();
        Mesh mesh = part.mesh();
        if (mesh.isDisposed() || part.primitiveTopology() != PrimitiveTopology.TRIANGLE_LIST || !Mesh.isPbrLayout(mesh.vertexLayout())
                || mesh.hasPbrSkinning() && nodePart.skin()==null) throw new IllegalArgumentException("LOD generation requires live standard PBR triangles and valid skin mappings");
        float[][] channels = {mesh.sourcePositions(),mesh.sourceColors(),mesh.sourceBakedColors(),mesh.sourceNormals(),
                mesh.sourceTexCoords(),mesh.sourceTexCoords1(),mesh.sourceTangents(),mesh.sourcePbr(),mesh.sourceBakedPbr(),
                mesh.sourceEmissive(),mesh.sourceBakedEmissive()};
        if (channels[0] == null || channels[3] == null || channels[4] == null || channels[7] == null || channels[9] == null
                || mesh.hasVertexColors() && channels[1] == null)
            throw new IllegalArgumentException("Missing CPU attributes; load with G3DAssetLoaders.modelLoader(graphics, true) before optimizing");
        int count = part.indexCount() > 0 ? part.indexCount() : part.vertexCount();
        int[] indices = new int[count];
        if (part.indexCount() > 0) {
            if (mesh.sourceIndices() == null || (long)part.firstIndex() + count > mesh.sourceIndices().length)
                throw new IllegalArgumentException("Missing or invalid retained index range");
            for (int i = 0; i < count; i++) indices[i] = mesh.sourceIndices()[part.firstIndex() + i] & 65535;
        } else for (int i = 0; i < count; i++) indices[i] = part.firstVertex() + i;
        return new MeshLodData(channels,indices,deformation);
    }
    private Skin copySkin(Skin source) {
        if (source==null) return null;
        Skin result=copiedSkins.get(source); if (result!=null) return result;
        io.github.libfdx.collections.Array<Bone> bones=new io.github.libfdx.collections.Array<>();
        for (int i=0;i<source.skeleton().bones().size();i++) {
            Bone bone=source.skeleton().bones().get(i);
            bones.add(new Bone(bone.id(),bone.parentIndex(),new Matrix4(bone.inverseBindTransform())));
        }
        result=new Skin(source.id(),new Skeleton(bones)); copiedSkins.put(source,result); skins.add(result);return result;
    }
}
