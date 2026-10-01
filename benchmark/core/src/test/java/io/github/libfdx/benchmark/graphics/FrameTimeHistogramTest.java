package io.github.libfdx.benchmark.graphics;

import java.util.Random;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class FrameTimeHistogramTest {
    @Test
    void matchesTheOriginalMappingAtEveryPowerOfTwoAndSubdivisionBoundary() {
        assertOriginalBucket(0L);
        for (int exponent = 0; exponent <= 62; exponent++) {
            long base = 1L << exponent;
            int subdivisions = exponent < 5 ? 1 << exponent : 32;
            long width = 1L << Math.max(0, exponent - 5);
            for (int subdivision = 0; subdivision < subdivisions; subdivision++) {
                long boundary = base + subdivision * width;
                assertOriginalBucket(boundary - 1L);
                assertOriginalBucket(boundary);
                assertOriginalBucket(boundary + 1L);
            }
        }
        assertOriginalBucket(Long.MAX_VALUE - 1L);
        assertOriginalBucket(Long.MAX_VALUE);
    }

    @Test
    void matchesTheOriginalMappingForDurationsThroughoutTheLongRange() {
        Random random = new Random(0x51f15e2dL);
        for (int i = 0; i < 4096; i++) {
            long nanos = random.nextLong() & Long.MAX_VALUE;
            assertOriginalBucket(nanos);
            assertOriginalBucket(nanos >>> (i % 63));
        }
    }

    private static void assertOriginalBucket(long nanos) {
        long upperBound = 0L;
        if (nanos > 0L) {
            int exponent = 63 - Long.numberOfLeadingZeros(nanos);
            int shift = Math.max(0, exponent - 5);
            int subdivision = (int)((nanos - (1L << exponent)) >>> shift);
            upperBound = exponent == 62 && subdivision == 31 ? Long.MAX_VALUE
                    : (1L << exponent) + ((long)(subdivision + 1) << shift) - 1L;
        }
        FrameTimeHistogram histogram = new FrameTimeHistogram();
        histogram.record(nanos);
        assertEquals(upperBound + ":1", histogram.encode(), "duration=" + nanos);
        assertEquals(nanos, histogram.percentileNanos(1.0));
    }
}
