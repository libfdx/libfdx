package io.github.libfdx.graphics.shader.runtime;

/**
 * Immutable, explicitly allocated diagnostic snapshot. Durations are wall-clock nanoseconds,
 * not CPU time or render stalls. Phase residence includes asynchronous waits; cache operations
 * may overlap phases and each other, and writes may finish after publication. Zero means no
 * measured work; unavailable measurements return -1 (or null for cache counters).
 */
public final class ShaderPreparationTimings {
    private final long queueNanos, preparationNanos, firstDrawNanos, readyToFirstDrawNanos, firstDrawUpdate;
    private final long[] phases;
    private final long[][] caches;

    ShaderPreparationTimings(long queueNanos, long preparationNanos, long firstDrawNanos,
            long readyToFirstDrawNanos, long firstDrawUpdate, long[] phases, long[][] caches) {
        this.queueNanos = queueNanos;
        this.preparationNanos = preparationNanos;
        this.firstDrawNanos = firstDrawNanos;
        this.readyToFirstDrawNanos = readyToFirstDrawNanos;
        this.firstDrawUpdate = firstDrawUpdate;
        this.phases = phases;
        this.caches = caches;
    }

    /** Service queue before submission; provider worker queue is phase QUEUED. */
    public long queueNanos() { return queueNanos; }
    /** Submission through publication, including publication wait; live while preparing. */
    public long preparationNanos() { return preparationNanos; }
    /** Enqueue to first successfully recorded nonempty draw, or -1 if not observed. */
    public long firstDrawNanos() { return firstDrawNanos; }
    public long readyToFirstDrawNanos() { return readyToFirstDrawNanos; }
    /** Service update index at first draw; not a GPU completion/presentation timestamp. */
    public long firstDrawUpdate() { return firstDrawUpdate; }
    public boolean phasesAvailable() { return phases != null; }
    public long phaseNanos(ShaderPreparationPhase phase) { return phases == null ? -1 : phases[phase.ordinal()]; }
    public long cacheReadNanos(ShaderCacheLayer layer) { return caches == null ? -1 : caches[layer.ordinal()][11]; }
    public long cacheWriteNanos(ShaderCacheLayer layer) { return caches == null ? -1 : caches[layer.ordinal()][12]; }
    /** Completed cache operations and explicit compiler/driver calls for this operation.
     * Shared/coalesced work belongs to its initiating operation, not every consumer. */
    public ShaderArtifactCache.Metrics cacheMetrics(ShaderCacheLayer layer) {
        if (caches == null) return null;
        long[] c = caches[layer.ordinal()];
        return new ShaderArtifactCache.Metrics(c[0], c[1], c[2], c[3], c[4], c[5], c[6], c[7], c[8], c[9], c[10]);
    }
}
