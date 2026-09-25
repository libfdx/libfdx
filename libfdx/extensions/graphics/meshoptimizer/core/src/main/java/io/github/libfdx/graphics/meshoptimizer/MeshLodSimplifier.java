package io.github.libfdx.graphics.meshoptimizer;

/**
 * CPU simplification strategy. Must not mutate the snapshot or access graphics; may run on a worker.
 * Preserve all channels and every {@link MeshLodData#vertexLocked(int) locked vertex} with its
 * attributes and incident boundary edges. The generator uses these constraints to protect
 * shared primitive boundaries. Set {@link MeshLodResult#unchanged()} only for equivalent geometry.
 */
@FunctionalInterface
public interface MeshLodSimplifier {
    MeshLodResult simplify(MeshLodData source, ModelLodTarget target, ModelLodSettings settings);

    /** Override to yield between work units; the default completes synchronously on its first step. */
    default MeshLodTask begin(MeshLodData source, ModelLodTarget target, ModelLodSettings settings) {
        return new ImmediateMeshLodTask(this, source, target, settings);
    }
}
