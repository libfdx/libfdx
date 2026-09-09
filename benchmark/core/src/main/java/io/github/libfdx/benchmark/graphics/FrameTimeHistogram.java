package io.github.libfdx.benchmark.graphics;

/**
 * Bounded, allocation-free recording of nonnegative nanosecond durations.
 * Positive durations use 32 subdivisions per power of two. Percentiles are
 * upper bounds with less than 3.125% bucket error, capped by the observed maximum.
 */
final class FrameTimeHistogram {
    static final long HITCH_NANOS = 50_000_000L;
    private static final int SUBDIVISIONS = 32;
    private final long[] buckets = new long[1 + 63 * SUBDIVISIONS];
    private long count;
    private double totalNanos;
    private long worstNanos;
    private long hitches;

    void record(long nanos) {
        if (nanos < 0L) {
            throw new IllegalArgumentException("Frame duration cannot be negative");
        }
        int bucket = 0;
        if (nanos > 0L) {
            int exponent = 63 - Long.numberOfLeadingZeros(nanos);
            int shift = Math.max(0, exponent - 5);
            bucket = 1 + exponent * SUBDIVISIONS + (int)((nanos - (1L << exponent)) >>> shift);
        }
        buckets[bucket]++;
        count++;
        totalNanos += nanos;
        worstNanos = Math.max(worstNanos, nanos);
        if (nanos > HITCH_NANOS) {
            hitches++;
        }
    }

    long count() {
        return count;
    }

    double totalNanos() {
        return totalNanos;
    }

    double meanNanos() {
        return count == 0L ? 0.0 : totalNanos / count;
    }

    long worstNanos() {
        return worstNanos;
    }

    long hitches() {
        return hitches;
    }

    long percentileNanos(double quantile) {
        if (!(quantile > 0.0 && quantile <= 1.0)) {
            throw new IllegalArgumentException("Quantile must be in (0, 1]");
        }
        long rank = (long)Math.ceil(quantile * count);
        long cumulative = 0L;
        for (int i = 0; i < buckets.length; i++) {
            cumulative += buckets[i];
            if (cumulative >= rank) {
                return Math.min(upperBoundNanos(i), worstNanos);
            }
        }
        return worstNanos;
    }

    /** Serializes nonempty buckets as comma-separated upperBoundNanos:count pairs. */
    String encode() {
        StringBuilder output = new StringBuilder();
        for (int i = 0; i < buckets.length; i++) {
            if (buckets[i] == 0L) {
                continue;
            }
            if (output.length() > 0) {
                output.append(',');
            }
            output.append(upperBoundNanos(i)).append(':').append(buckets[i]);
        }
        return output.toString();
    }

    private long upperBoundNanos(int bucket) {
        if (bucket == 0) {
            return 0L;
        }
        int exponent = (bucket - 1) / SUBDIVISIONS;
        int subdivision = (bucket - 1) % SUBDIVISIONS;
        if (exponent == 62 && subdivision == SUBDIVISIONS - 1) {
            return Long.MAX_VALUE;
        }
        int shift = Math.max(0, exponent - 5);
        return (1L << exponent) + ((long)(subdivision + 1) << shift) - 1L;
    }
}
