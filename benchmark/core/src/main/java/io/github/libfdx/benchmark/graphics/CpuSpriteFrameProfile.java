package io.github.libfdx.benchmark.graphics;

/** Coarse, allocation-free frame accounting for the single-batch diagnostic. */
final class CpuSpriteFrameProfile {
    static final int BEGIN = 0;
    static final int FILL = 1;
    static final int STAGING = 2;
    static final int UPLOAD = 3;
    static final int SUBMIT = 4;
    static final int END = 5;
    static final int OTHER = 6;
    static final int OUTSIDE = 7;
    static final String[] NAMES = {"Begin", "Fill", "Staging", "Upload", "Submit", "End", "Other", "Outside"};

    private final long warmupNanos;
    private final long[] current = new long[6];
    private final long[] previous = new long[7];
    private final long[] totals = new long[8];
    private boolean started;
    private long firstStart;
    private long previousStart;
    private long previousEnd;
    private long intervals;

    CpuSpriteFrameProfile(long warmupNanos) {
        this.warmupNanos = warmupNanos;
    }

    void add(int phase, long nanos) {
        current[phase] += nanos;
    }

    void recordFrame(long start, long end) {
        if (started && previousStart - firstStart >= warmupNanos) {
            for (int i = 0; i < previous.length; i++) totals[i] += previous[i];
            totals[OUTSIDE] += start - previousEnd;
            intervals++;
        }
        if (!started) {
            firstStart = start;
            started = true;
        }
        long measured = 0;
        for (int i = 0; i < current.length; i++) {
            previous[i] = current[i];
            measured += current[i];
            current[i] = 0;
        }
        previous[OTHER] = end - start - measured;
        previousStart = start;
        previousEnd = end;
    }

    long intervals() { return intervals; }
    long total(int phase) { return totals[phase]; }
}
