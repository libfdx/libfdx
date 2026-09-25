package io.github.libfdx.graphics.meshoptimizer;

import io.github.libfdx.collections.Array;
import io.github.libfdx.core.Disposable;
import io.github.libfdx.graphics.GraphicsContext;
import io.github.libfdx.graphics.Mesh;
import io.github.libfdx.graphics.PrimitiveTopology;
import io.github.libfdx.graphics.g3d.*;
import java.util.IdentityHashMap;

/** Incremental graphics-thread upload. Generated levels become visible to the caller together, at take(). */
public final class ModelLodUpload implements Disposable {
    private final GraphicsContext graphics;
    private final PreparedModelLods prepared;
    private final Model[] models;
    private final Array<Mesh> pending = new Array<>();
    private final IdentityHashMap<LodPreparedMesh[],Mesh[]> completed = new IdentityHashMap<>();
    private Mesh[][] geometry;
    private Mesh.PositionColor3DUpload active;
    private int level, part, chunk;
    private boolean disposed, taken;

    ModelLodUpload(GraphicsContext graphics, PreparedModelLods prepared) {
        this.graphics = graphics; this.prepared = prepared; models = new Model[prepared.meshes.length];
    }

    /** Uploads at most one mesh's vertex byte budget per call. Index upload/allocation are indivisible. */
    public boolean step(int maxVertexBytes) {
        if (disposed || taken) throw new IllegalStateException("LOD upload no longer owned");
        if (maxVertexBytes < 4) throw new IllegalArgumentException("Upload budget must be at least four bytes");
        if (level == models.length) return true;
        try {
            if (prepared.input.source.isDisposed()) throw new IllegalStateException("Base model disposed during LOD upload");
            if (geometry == null) {
                geometry = new Mesh[prepared.meshes[level].length][];
            }
            LodPreparedMesh[] preparedPart=prepared.meshes[level][part];
            if(preparedPart==null) return finishPart(); // Borrow the original mesh and range.
            Mesh[] reused=completed.get(preparedPart);
            if(reused!=null) { geometry[part]=reused;return finishPart(); }
            if(geometry[part]==null) geometry[part]=new Mesh[preparedPart.length];
            LodPreparedMesh next = prepared.meshes[level][part][chunk];
            if (active == null) active = next.preparation().beginUpload(graphics,"LOD " + (level+1) + "/" + part + "/" + chunk,next.indices());
            if (!active.step(maxVertexBytes)) return false;
            Mesh mesh = active.take(); active.dispose(); active = null;
            geometry[part][chunk] = mesh; pending.add(mesh);
            if (++chunk < geometry[part].length) return false;
            completed.put(preparedPart,geometry[part]);
            return finishPart();
        } catch (RuntimeException | Error failure) {
            try { dispose(); } catch (RuntimeException | Error cleanup) { if (cleanup != failure) failure.addSuppressed(cleanup); }
            throw failure;
        }
    }
    private boolean finishPart() {
            chunk=0;
            if (++part < geometry.length) return false;
            Array<ModelNode> roots = new Array<>();
            for (LodNodeSnapshot root : prepared.input.roots) roots.add(node(root));
            Array<Material> materials = new Array<>();
            for (Material material : prepared.input.materials) materials.add(material);
            Array<AnimationClip> animations=new Array<>();for (AnimationClip clip : prepared.input.animations) animations.add(clip);
            Array<Skin> skins=new Array<>();for (Skin skin : prepared.input.skins) skins.add(skin);
            models[level++] = new DefaultModel(roots,materials,animations,skins,pending);
            pending.clear(); geometry = null; part = 0;
            return level == models.length;
    }

    private ModelNode node(LodNodeSnapshot source) {
        ModelNode node = new ModelNode(source.id()).localTransform(source.transform()).morphWeights(source.morphWeights());
        for (LodPartSnapshot part : source.parts()) {
            Mesh[] meshes=geometry[part.geometry()];
            if(meshes==null) {
                MeshPart original=prepared.input.sourceParts.get(part.geometry());
                ModelNodePart originalPart=prepared.input.sourceNodeParts.get(part.geometry());
                node.addPart(new ModelNodePart(new MeshPart(part.id(),original.mesh(),original.primitiveTopology(),original.firstVertex(),original.vertexCount(),original.firstIndex(),original.indexCount()),
                        part.material(),part.skin(),part.skin()==null ? null : originalPart.joints(),part.skin()==null ? null : originalPart.weights(),part.morphTargets()));
            } else for (int i=0;i<meshes.length;i++) {
                Mesh mesh=meshes[i];
                node.addPart(new ModelNodePart(new MeshPart(part.id(),mesh,PrimitiveTopology.TRIANGLE_LIST,
                        0,mesh.vertexCount(),0,mesh.indexCount()),part.material(),part.skin(),null,null,prepared.meshes[level][part.geometry()][i].morphTargets()));
            }
        }
        for (LodNodeSnapshot child : source.children()) node.addChild(node(child));
        return node;
    }

    public GeneratedModelLods take() {
        if (disposed || taken || level != models.length) throw new IllegalStateException("LOD upload is incomplete or no longer owned");
        GeneratedModelLods result = new GeneratedModelLods(prepared.input.source,models,prepared.settings,prepared.reports);
        taken = true;
        return result;
    }
    @Override public boolean isDisposed() { return disposed; }
    @Override public void dispose() {
        if (disposed) return;
        disposed = true;
        Throwable failure = null;
        if (active != null) failure = close(active,failure);
        for (int i = 0; i < pending.size(); i++) failure = close(pending.get(i),failure);
        pending.clear();
        completed.clear();
        if (!taken) for (Model model : models) if (model != null) failure = close(model,failure);
        if (failure instanceof RuntimeException e) throw e;
        if (failure instanceof Error e) throw e;
    }
    private static Throwable close(Disposable resource, Throwable failure) {
        try { resource.dispose(); } catch (RuntimeException | Error next) {
            if (failure == null) return next;
            if (failure != next) failure.addSuppressed(next);
        }
        return failure;
    }
}
