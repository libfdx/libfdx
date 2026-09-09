package io.github.libfdx.benchmark.graphics;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class BenchmarkFrameTimingTest {
    @Test
    void excludesWarmupAndPairsCpuWorkWithItsFollowingPresentationInterval() {
        BenchmarkFrameTiming timing = new BenchmarkFrameTiming(100L);
        timing.recordFrame(-1000L, -960L);
        timing.recordFrame(-940L, -920L);
        timing.recordFrame(-900L, -885L);
        assertEquals(0L, timing.intervals().count());

        timing.recordFrame(-870L, -865L);
        timing.recordFrame(-850L, -846L);

        assertEquals(2L, timing.warmupFrames());
        assertEquals(2L, timing.intervals().count());
        assertEquals(50.0, timing.intervals().totalNanos());
        assertEquals(20.0, timing.cpu().totalNanos());
        assertEquals(40_000_000.0, timing.framesPerSecond());
    }

    @Test
    void zeroWarmupStillRequiresOneCompleteInterval() {
        BenchmarkFrameTiming timing = new BenchmarkFrameTiming(0L);
        timing.recordFrame(0L, 5L);
        assertEquals(0L, timing.intervals().count());
        assertEquals(0.0, timing.framesPerSecond());
        timing.recordFrame(10L, 11L);
        assertEquals(1L, timing.intervals().count());
        assertEquals(5.0, timing.cpu().meanNanos());
        assertEquals(0L, timing.warmupFrames());
        assertThrows(IllegalArgumentException.class, () -> timing.recordFrame(9L, 12L));
        assertThrows(IllegalArgumentException.class, () -> new BenchmarkFrameTiming(-1L));
    }

    @Test
    void histogramsRetainAllSamplesAndBoundPercentileError() {
        FrameTimeHistogram histogram = new FrameTimeHistogram();
        for (int i = 1; i <= 100; i++) {
            histogram.record(i * 1_000_000L);
        }
        assertEquals(100L, histogram.count());
        assertEquals(50_500_000.0, histogram.meanNanos());
        assertEquals(100_000_000L, histogram.worstNanos());
        assertEquals(50L, histogram.hitches());
        for (double quantile : new double[] {0.5, 0.95, 0.99, 1.0}) {
            long actual = (long)(quantile * 100_000_000L);
            long estimate = histogram.percentileNanos(quantile);
            assertTrue(estimate >= actual);
            assertTrue(estimate <= actual * 1.03125);
        }
        long encodedCount = 0L;
        for (String bucket : histogram.encode().split(",")) {
            encodedCount += Long.parseLong(bucket.substring(bucket.indexOf(':') + 1));
        }
        assertEquals(histogram.count(), encodedCount);
    }

    @Test
    void handlesEmptyZeroAndExtremeDurationsWithoutBucketOverflow() {
        FrameTimeHistogram histogram = new FrameTimeHistogram();
        assertEquals(0L, histogram.percentileNanos(0.99));
        assertEquals("", histogram.encode());
        histogram.record(0L);
        histogram.record(1L);
        histogram.record(Long.MAX_VALUE);
        assertEquals(1L, histogram.percentileNanos(0.5));
        assertEquals(Long.MAX_VALUE, histogram.percentileNanos(1.0));
        assertEquals("0:1,1:1,9223372036854775807:1", histogram.encode());
        assertThrows(IllegalArgumentException.class, () -> histogram.record(-1L));
        assertThrows(IllegalArgumentException.class, () -> histogram.percentileNanos(Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> histogram.percentileNanos(0.0));
        assertThrows(IllegalArgumentException.class, () -> histogram.percentileNanos(1.01));
    }
}
