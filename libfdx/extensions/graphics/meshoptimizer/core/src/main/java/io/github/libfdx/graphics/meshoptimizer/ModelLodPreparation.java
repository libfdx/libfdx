package io.github.libfdx.graphics.meshoptimizer;

import java.util.Objects;
import java.util.function.BooleanSupplier;

/**
 * Single-owner cooperative preparation for event loops or CPU workers. Ordering, channel
 * compaction, boundary preparation and packing yield between steps. Individual allocations,
 * high-valence topology checks and GC are not hard real-time operations.
 */
public final class ModelLodPreparation {
    private final MeshLodSimplifier simplifier;
    private final ModelLodInput source;
    private final ModelLodSettings settings;
    private final BooleanSupplier cancelled;
    private final LodPreparedMesh[][][] levels;
    private final MeshLodData[] inputs;
    private final MeshLodData[][] reduced;
    private final ModelLodReport[] reports;
    private LodMeshWeldTask weld;
    private LodSharedVerticesTask shared;
    private MeshLodTask mesh;
    private MeshLodResult current;
    private LodMeshPackingTask packing;
    private LodMeshEqualityTask comparison;
    private int phase, level, geometry, compareLevel, triangles, vertices, parts;
    private float error;
    private PreparedModelLods result;
    ModelLodPreparation(MeshLodSimplifier simplifier, ModelLodInput source, ModelLodSettings settings, BooleanSupplier cancelled) {
        this.simplifier=simplifier; this.source=Objects.requireNonNull(source);
        this.settings=Objects.requireNonNull(settings);this.cancelled=Objects.requireNonNull(cancelled);
        inputs=new MeshLodData[source.geometry.size()];
        levels=new LodPreparedMesh[settings.levelCount()][inputs.length][];
        reduced=new MeshLodData[settings.levelCount()][inputs.length];
        reports=new ModelLodReport[settings.levelCount()];
    }
    /** Advances one preparation stage; cancellation is checked at every step. */
    public boolean step(int budget) {
        if(budget<1) throw new IllegalArgumentException("Positive work budget required");
        LodMeshPacking.check(cancelled);
        if(result!=null) return true;
        if(phase==0) {
            if(weld==null) weld=new LodMeshWeldTask(source.geometry.get(geometry));
            if(!weld.step(budget)) return false;
            inputs[geometry++]=weld.result();weld=null;
            if(geometry==inputs.length) { geometry=0;shared=new LodSharedVerticesTask(inputs,source.rigidGroups);phase++; }
            return false;
        }
        if(phase==1) {
            if(!shared.step(budget)) return false;
            shared=null;phase++;return false;
        }
        MeshLodData original=inputs[geometry];
        if(phase==2) {
            if(mesh==null) { mesh=simplifier.begin(original,settings.target(level),settings);return false; }
            if(!mesh.step(budget)) return false;
            current=mesh.result();mesh=null;
            if (current.mesh().deformation()!=original.deformation()) throw new IllegalArgumentException("Simplifier must retain skin/morph deformation provenance");
            if(current.mesh().triangleCount()>original.triangleCount()) throw new IllegalArgumentException("Simplifier increased triangle count");
            for(int c=0;c<MeshLodData.CHANNEL_COUNT;c++)
                if((original.channel(c)==null)!=(current.mesh().channel(c)==null)) throw new IllegalArgumentException("Simplifier changed vertex attribute schema");
            if(current.unchanged()) { finish(null);return result!=null; }
            reduced[level][geometry]=current.mesh();compareLevel=0;phase++;return false;
        }
        if(phase==3) {
            if(compareLevel==level) { packing=new LodMeshPackingTask(current.mesh(),cancelled);phase++;return false; }
            if(comparison==null) comparison=new LodMeshEqualityTask(current.mesh(),reduced[compareLevel][geometry]);
            if(!comparison.step(budget)) return false;
            boolean equal=comparison.equal();comparison=null;
            if(equal) { finish(levels[compareLevel][geometry]);return result!=null; }
            compareLevel++;return false;
        }
        if(!packing.step(budget)) return false;
        LodPreparedMesh[] packed=packing.result();packing=null;finish(packed);
        return result!=null;
    }
    private void finish(LodPreparedMesh[] packed) {
        levels[level][geometry]=packed;
        int uses=source.occurrences.get(geometry);
        if(packed==null) {
            triangles=Math.addExact(triangles,Math.multiplyExact(source.geometry.get(geometry).triangleCount(),uses));
            vertices=Math.addExact(vertices,Math.multiplyExact(source.sourceVertexCounts.get(geometry),uses));
            parts=Math.addExact(parts,uses);
        } else for(LodPreparedMesh item:packed) {
            triangles=Math.addExact(triangles,Math.multiplyExact(item.indices().length/3,uses));
            vertices=Math.addExact(vertices,Math.multiplyExact(item.vertices(),uses));parts=Math.addExact(parts,uses);
        }
        error=Math.max(error,current.error());current=null;phase=2;
        if(++geometry==inputs.length) {
            int desired=Math.max(1,(int)Math.floor(source.triangleCount()*(double)settings.target(level).triangleRatio()));
            reports[level]=new ModelLodReport(level+1,source.triangleCount(),triangles,vertices,parts,error,triangles<=desired);
            geometry=triangles=vertices=parts=0;error=0;
            if(++level==levels.length) result=new PreparedModelLods(source,settings,levels,reports);
        }
    }
    /** Completed preparation, ready for publication to the graphics thread. */
    public PreparedModelLods result() { if(result==null) throw new IllegalStateException("LOD preparation is incomplete");return result; }
}
