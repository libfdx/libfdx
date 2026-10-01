package io.github.libfdx.testsupport;

/** Describes the actual quality and resource choices made by a measured scene. */
public interface PerformanceWorkload {
    /** A deterministic, whitespace-free description; omit timings and provider names. */
    String performanceWorkload();
}
