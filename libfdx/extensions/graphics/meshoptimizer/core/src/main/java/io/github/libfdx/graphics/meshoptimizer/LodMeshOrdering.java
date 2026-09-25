package io.github.libfdx.graphics.meshoptimizer;

/** Blocking adapter for callers already running on a preparation worker. */
final class LodMeshOrdering {
    private LodMeshOrdering() { }
    static MeshLodData compact(MeshLodData source,int[] indices,boolean cache,boolean fetch) {
        LodMeshOrderingTask task=new LodMeshOrderingTask(source,indices,cache,fetch);
        while(!task.step(4096)) { }
        return task.result();
    }
}
