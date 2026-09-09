package io.github.libfdx.testsupport.graphics;

import java.util.Arrays;

/** Bounded frame samples. Recording allocates nothing; sorting happens only when reporting. */
public final class ShaderFrameTimings {
    private final long[] samples = new long[16_384];
    private long frames, maximum;
    private int size;

    public void record(long nanos) {
        frames++;
        maximum = Math.max(maximum, nanos);
        if (size < samples.length) samples[size++] = nanos;
    }

    /** Percentiles cover the first 16,384 samples; maximum and frame count cover every sample. */
    public Snapshot snapshot() {
        long[] sorted = Arrays.copyOf(samples, size);
        Arrays.sort(sorted);
        return new Snapshot(frames, frames - size, percentile(sorted, .50), percentile(sorted, .95),
                percentile(sorted, .99), maximum / 1_000_000.0);
    }

    private static double percentile(long[] sorted, double percentile) {
        return sorted.length == 0 ? 0 : sorted[(int) Math.ceil(sorted.length * percentile) - 1] / 1_000_000.0;
    }

    public record Snapshot(long frames, long unsampledFrames, double p50Millis, double p95Millis,
                           double p99Millis, double maxMillis) { }
}
