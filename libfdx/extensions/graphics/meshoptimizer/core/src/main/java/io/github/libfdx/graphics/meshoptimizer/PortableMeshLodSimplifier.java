package io.github.libfdx.graphics.meshoptimizer;

import java.util.Objects;

/**
 * Deterministic pure Java edge-collapse optimizer. No JNI, processes, threads or platform APIs.
 * Uses normalized area-weighted geometry/attribute quadrics, endpoint collapses, manifold/link
 * and face-orientation guards. Paired seam edges collapse together; seam junctions and
 * nonmanifold vertices stay fixed. Targets may
 * be unattainable under these constraints. Error is a relative quadric RMS, not a Hausdorff bound.
 * All retained channels are remapped together; surviving vertex attributes are never interpolated.
 */
public final class PortableMeshLodSimplifier implements MeshLodSimplifier {
    @Override public MeshLodResult simplify(MeshLodData source, ModelLodTarget target, ModelLodSettings settings) {
        MeshLodTask task = begin(source, target, settings);
        while (!task.step(4096)) { /* Synchronous preparation; use begin() on event loops. */ }
        return task.result();
    }
    @Override public MeshLodTask begin(MeshLodData source, ModelLodTarget target, ModelLodSettings settings) {
        return new PortableMeshLodTask(Objects.requireNonNull(source), Objects.requireNonNull(target), Objects.requireNonNull(settings));
    }
}
