package io.github.libfdx.graphics.meshoptimizer;

import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;

/** Blocking adapter for CPU workers; cooperative callers retain the packing job. */
final class LodMeshPacking {
    static LodPreparedMesh[] pack(MeshLodData data,BooleanSupplier cancelled) {
        LodMeshPackingTask task=new LodMeshPackingTask(data,cancelled);
        while(!task.step(4096)) { }
        return task.result();
    }
    static void check(BooleanSupplier cancelled) { if(cancelled.getAsBoolean()) throw new CancellationException("LOD generation cancelled"); }
}
