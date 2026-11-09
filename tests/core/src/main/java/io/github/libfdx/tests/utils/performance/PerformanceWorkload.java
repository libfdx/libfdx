package io.github.libfdx.tests.utils.performance;

/** Describes the actual quality and resource choices made by a measured scene. */
public interface PerformanceWorkload {
    /** A deterministic, whitespace-free description; omit timings and provider names. */
    String performanceWorkload();
}
