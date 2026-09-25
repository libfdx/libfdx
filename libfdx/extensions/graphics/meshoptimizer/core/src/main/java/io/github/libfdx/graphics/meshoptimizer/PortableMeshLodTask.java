package io.github.libfdx.graphics.meshoptimizer;

/** Separates exact welding and simplifier allocation into cooperative steps. */
final class PortableMeshLodTask implements MeshLodTask {
    private LodMeshWeldTask weld;
    private final ModelLodTarget target;
    private final ModelLodSettings settings;
    private MeshLodTask task;
    PortableMeshLodTask(MeshLodData source,ModelLodTarget target,ModelLodSettings settings) {
        weld=new LodMeshWeldTask(source); this.target=target; this.settings=settings;
    }
    @Override public boolean step(int budget) {
        if(budget<1) throw new IllegalArgumentException("Positive work budget required");
        if(task==null) {
            if(!weld.step(budget)) return false;
            task=new QuadricMeshLodTask(weld.result(),target,settings); weld=null; return false;
        }
        return task.step(budget);
    }
    @Override public MeshLodResult result() { if(task==null) throw new IllegalStateException("Simplification incomplete"); return task.result(); }
}
