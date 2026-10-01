package io.github.libfdx.testsupport;

/** Measures complete frame intervals, excluding initialization and warm-up. */
final class FrameTiming {
    private final long warmupNanos;
    private final long warmupFrames;
    private final Histogram intervals = new Histogram();
    private final Histogram render = new Histogram();
    private boolean started;
    private long firstStart;
    private long previousStart;
    private long previousEnd;
    private long frames;

    FrameTiming(long warmupNanos) {
        this(warmupNanos, -1);
    }

    FrameTiming(long warmupNanos, long warmupFrames) {
        if (warmupNanos < 0) throw new IllegalArgumentException("Negative warm-up duration");
        if (warmupFrames < -1) throw new IllegalArgumentException("Negative warm-up frame count");
        this.warmupNanos = warmupNanos;
        this.warmupFrames = warmupFrames;
    }

    void record(long start, long end) {
        if (end < start || (started && start < previousEnd)) {
            throw new IllegalArgumentException("Frame timestamps must be ordered");
        }
        if (!started) {
            started = true;
            firstStart = start;
        } else if (warmupFrames >= 0
                ? frames > warmupFrames
                : previousStart - firstStart >= warmupNanos) {
            intervals.record(start - previousStart);
            render.record(previousEnd - previousStart);
        }
        previousStart = start;
        previousEnd = end;
        frames++;
    }

    long samples() {
        return intervals.samples;
    }

    long measuredNanos() {
        return intervals.total;
    }

    double fps() {
        return intervals.total == 0 ? 0 : intervals.samples * 1e9 / intervals.total;
    }

    double meanMillis() {
        return intervals.meanMillis();
    }

    double renderMillis() {
        return render.meanMillis();
    }

    double percentileMillis(double fraction) {
        return intervals.percentileMillis(fraction);
    }

    private static final class Histogram {
        // 10 microsecond bins through two seconds; overflowing tails retain their maximum.
        private final int[] buckets = new int[200_001];
        private long samples;
        private long total;
        private long maximum;

        void record(long nanos) {
            samples++;
            total += nanos;
            maximum = Math.max(maximum, nanos);
            buckets[(int) Math.min(buckets.length - 1L, nanos / 10_000)]++;
        }

        double meanMillis() {
            return samples == 0 ? 0 : total / (samples * 1e6);
        }

        double percentileMillis(double fraction) {
            if (samples == 0) return 0;
            long target = (long) Math.ceil(samples * fraction);
            long sum = 0;
            for (int i = 0; i < buckets.length; i++) {
                sum += buckets[i];
                if (sum >= target) {
                    return i == buckets.length - 1 ? maximum / 1e6 : (i + 1) * 0.01;
                }
            }
            return maximum / 1e6;
        }
    }
}
