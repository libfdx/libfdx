package io.github.libfdx.testsupport;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class FrameTimingTest {
    @Test
    void measuresWholeIntervalsAndMatchingRenderTimeAfterWarmup() {
        var timing = new FrameTiming(20_000_000);
        timing.record(100_000_000, 103_000_000);
        timing.record(110_000_000, 114_000_000);
        timing.record(120_000_000, 125_000_000);
        timing.record(140_000_000, 147_000_000);
        timing.record(170_000_000, 178_000_000);
        assertEquals(2, timing.samples());
        assertEquals(50_000_000, timing.measuredNanos());
        assertEquals(40, timing.fps());
        assertEquals(25, timing.meanMillis());
        assertEquals(6, timing.renderMillis());
        assertEquals(30.01, timing.percentileMillis(0.95), 0.001);
    }

    @Test
    void preservesLongStallsAndRejectsOutOfOrderTimestamps() {
        var timing = new FrameTiming(0);
        timing.record(0, 1);
        timing.record(3_000_000_000L, 3_000_000_001L);
        assertEquals(3000, timing.percentileMillis(0.99));
        assertThrows(IllegalArgumentException.class, () -> timing.record(10, 11));
    }

    @Test
    void frameBasedWarmupSelectsTheSameWorkAtDifferentExecutionSpeeds() {
        var quick = new FrameTiming(0, 2);
        var slow = new FrameTiming(0, 2);
        for (int frame = 0; frame < 6; frame++) {
            quick.record(frame * 1_000_000L, frame * 1_000_000L + 400_000);
            slow.record(frame * 5_000_000L, frame * 5_000_000L + 3_000_000);
        }
        assertEquals(3, quick.samples());
        assertEquals(quick.samples(), slow.samples());
        assertEquals(1000, quick.fps());
        assertEquals(200, slow.fps());
        assertEquals(0.4, quick.renderMillis());
        assertEquals(3, slow.renderMillis());
    }

    @Test
    void zeroFrameWarmupIncludesFirstCompleteInterval() {
        var timing = new FrameTiming(0, 0);
        timing.record(100, 120);
        assertEquals(0, timing.samples());
        timing.record(200, 230);
        assertEquals(1, timing.samples());
        assertEquals(100, timing.measuredNanos());
        assertEquals(0.00002, timing.renderMillis());
    }
}
