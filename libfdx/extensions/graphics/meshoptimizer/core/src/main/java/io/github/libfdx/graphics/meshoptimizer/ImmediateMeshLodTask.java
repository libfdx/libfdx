package io.github.libfdx.graphics.meshoptimizer;

/** Adapts a synchronous CPU simplifier to the preparation-task interface. */
final class ImmediateMeshLodTask implements MeshLodTask {
    private final MeshLodSimplifier simplifier;
    private final MeshLodData source;
    private final ModelLodTarget target;
    private final ModelLodSettings settings;
    private MeshLodResult result;
    ImmediateMeshLodTask(MeshLodSimplifier simplifier, MeshLodData source, ModelLodTarget target, ModelLodSettings settings) {
        this.simplifier = simplifier; this.source = source; this.target = target; this.settings = settings;
    }
    @Override public boolean step(int budget) {
        if (budget < 1) throw new IllegalArgumentException("Positive work budget required");
        if (result == null) result = simplifier.simplify(source, target, settings);
        return true;
    }
    @Override public MeshLodResult result() {
        if (result == null) throw new IllegalStateException("Simplification is incomplete");
        return result;
    }
}
