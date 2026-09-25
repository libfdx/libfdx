package io.github.libfdx.graphics.meshoptimizer;

import java.util.Objects;
import java.util.function.BooleanSupplier;

/** Application-owned CPU preparation service; use begin on event loops or prepare on a CPU worker. */
public final class ModelLodOptimizer {
    private final MeshLodSimplifier simplifier;
    /** Portable default; does not load native libraries or start threads. */
    public ModelLodOptimizer() { this(new PortableMeshLodSimplifier()); }
    public ModelLodOptimizer(MeshLodSimplifier simplifier) { this.simplifier = Objects.requireNonNull(simplifier); }

    public PreparedModelLods prepare(ModelLodInput source, ModelLodSettings settings) {
        return prepare(source,settings,() -> false);
    }

    /**
     * Generates every level from the original snapshot, avoiding accumulated errors. No graphics
     * calls occur here. Cancellation is checked between simplifier steps and packing chunks.
     * Inputs remain unchanged.
     */
    public PreparedModelLods prepare(ModelLodInput source, ModelLodSettings settings, BooleanSupplier cancelled) {
        ModelLodPreparation task = begin(source,settings,cancelled);
        while (!task.step(1024)) { /* Explicit blocking preparation, suitable for a CPU worker. */ }
        return task.result();
    }

    /** Cooperative alternative for browsers and other applications without a worker. */
    public ModelLodPreparation begin(ModelLodInput source, ModelLodSettings settings) {
        return begin(source,settings,() -> false);
    }
    public ModelLodPreparation begin(ModelLodInput source, ModelLodSettings settings, BooleanSupplier cancelled) {
        return new ModelLodPreparation(simplifier,source,settings,cancelled);
    }
}
