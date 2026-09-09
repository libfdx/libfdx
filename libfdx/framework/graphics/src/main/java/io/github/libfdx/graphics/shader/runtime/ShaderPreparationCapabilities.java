package io.github.libfdx.graphics.shader.runtime;

import java.util.Objects;

/** Immutable execution capabilities of the selected device, not its API's theoretical features.
 * runtimeNonblocking permits ordinary runtime advancement; it is not a hard call-time bound.
 * DRIVER_POLLING still uses the context owner: native initialization and status queries can
 * stall even after the driver reports compilation complete. Preload before latency-sensitive use.
 * pipelineCache reports persistence availability, not a cache hit or nonblocking cache import.
 * Providers may restrict native persistence to explicit loading, as GL/GLES do. */
public record ShaderPreparationCapabilities(
        Execution cpuExecution, Execution nativeExecution,
        boolean runtimeNonblocking, int workerLimit,
        boolean artifactCache, boolean pipelineCache) {

    /** Execution mechanism. Native async completion does not imply controllable workers. */
    public enum Execution { UNAVAILABLE, OWNER_THREAD, WORKERS, NATIVE_ASYNC, DRIVER_POLLING }

    public static final ShaderPreparationCapabilities UNAVAILABLE = new ShaderPreparationCapabilities(
            Execution.UNAVAILABLE, Execution.UNAVAILABLE, false, 0, false, false);

    public ShaderPreparationCapabilities {
        Objects.requireNonNull(cpuExecution, "cpuExecution");
        Objects.requireNonNull(nativeExecution, "nativeExecution");
        if (workerLimit < 0) throw new IllegalArgumentException("workerLimit cannot be negative");
        if (runtimeNonblocking && (cpuExecution == Execution.UNAVAILABLE
                || nativeExecution == Execution.UNAVAILABLE || cpuExecution == Execution.OWNER_THREAD
                || nativeExecution == Execution.OWNER_THREAD)) {
            throw new IllegalArgumentException("Nonblocking preparation requires supported nonblocking stages");
        }
    }
}
