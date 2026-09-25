package io.github.libfdx.graphics.meshoptimizer;

/** Single-owner CPU job. A step budget counts work units, not elapsed time. No graphics calls. */
public interface MeshLodTask {
    /** Advances preparation; returns true when result() is available. Budget must be positive. */
    boolean step(int budget);
    /** Completed result; throws before completion. */
    MeshLodResult result();
}
