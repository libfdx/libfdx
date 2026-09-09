package io.github.libfdx.benchmark.graphics;

/**
 * Records complete start-to-start intervals after warm-up and their matching
 * CPU render durations. The last render has no following interval and is omitted.
 */
final class BenchmarkFrameTiming {
    private final long warmupNanos;
    private final FrameTimeHistogram intervals = new FrameTimeHistogram();
    private final FrameTimeHistogram cpu = new FrameTimeHistogram();
    private boolean started;
    private long firstStart;
    private long previousStart;
    private long previousEnd;
    private long warmupFrames;

    BenchmarkFrameTiming(long warmupNanos) {
        if (warmupNanos < 0L) {
            throw new IllegalArgumentException("Warm-up duration cannot be negative");
        }
        this.warmupNanos = warmupNanos;
    }

    void recordFrame(long startNanos, long endNanos) {
        if (endNanos - startNanos < 0L || (started && startNanos - previousEnd < 0L)) {
            throw new IllegalArgumentException("Frame timestamps must be ordered");
        }
        if (!started) {
            firstStart = startNanos;
            started = true;
        } else if (previousStart - firstStart >= warmupNanos) {
            intervals.record(startNanos - previousStart);
            cpu.record(previousEnd - previousStart);
        }
        if (startNanos - firstStart < warmupNanos) {
            warmupFrames++;
        }
        previousStart = startNanos;
        previousEnd = endNanos;
    }

    long warmupFrames() {
        return warmupFrames;
    }

    FrameTimeHistogram intervals() {
        return intervals;
    }

    FrameTimeHistogram cpu() {
        return cpu;
    }

    double framesPerSecond() {
        return intervals.totalNanos() <= 0.0 ? 0.0
                : intervals.count() * 1_000_000_000.0 / intervals.totalNanos();
    }
}
