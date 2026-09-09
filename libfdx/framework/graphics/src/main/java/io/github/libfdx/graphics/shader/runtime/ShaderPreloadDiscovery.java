package io.github.libfdx.graphics.shader.runtime;

import java.util.List;

/** Immutable observation snapshot. origin is the first observed origin; origins contains all
 * retained origins, including the first. Durations measure preparation latency, not render-thread
 * stalls or an expected FPS improvement. Timing snapshots include only work completed/observed by capture time.
 * A requirement is identified by provider instance, revision and structural request. Retries and
 * residency replacements retain its ID, initial demand frame/readiness/preload flags and cumulative
 * draw counts. Cause, outcome, failure and timings describe the latest observed preparation attempt;
 * drawing through an older retained handle cannot replace that attempt's diagnostics. */
public record ShaderPreloadDiscovery(long id, ShaderPreparationOrigin origin, List<ShaderPreparationOrigin> origins, ShaderRequest request,
        String provider, long firstNeededFrame, boolean readyWhenNeeded, boolean preloadDeclared,
        Cause cause, ShaderPreparationState state, long logicalDraws, long skippedDraws,
        long queueNanos, long preparationNanos, String failure, ShaderPreparationTimings timings) {
    public ShaderPreloadDiscovery { origins = List.copyOf(origins); }
    public enum Cause { NONE, NOT_PRELOADED, PRELOAD_TOO_LATE, CONFIGURATION_CHANGED, RESIDENCY_LOST, FAILED, UNSUPPORTED }
    public boolean actionable() { return cause != Cause.NONE; }
}
