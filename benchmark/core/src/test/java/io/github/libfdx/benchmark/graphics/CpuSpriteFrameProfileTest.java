package io.github.libfdx.benchmark.graphics;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class CpuSpriteFrameProfileTest {
    @Test
    void accountsForCompleteIntervalsAndExcludesWarmupAndUnpairedLastFrame() {
        CpuSpriteFrameProfile profile = new CpuSpriteFrameProfile(100);
        BenchmarkFrameTiming timing = new BenchmarkFrameTiming(100);
        long[] starts = {-1000, -950, -900, -850, -800};
        for (long start : starts) {
            profile.add(CpuSpriteFrameProfile.BEGIN, 2);
            profile.add(CpuSpriteFrameProfile.FILL, 10);
            profile.add(CpuSpriteFrameProfile.STAGING, 3);
            profile.add(CpuSpriteFrameProfile.UPLOAD, 4);
            profile.add(CpuSpriteFrameProfile.SUBMIT, 5);
            profile.add(CpuSpriteFrameProfile.END, 1);
            profile.recordFrame(start, start + 30);
            timing.recordFrame(start, start + 30);
        }
        assertEquals(2, profile.intervals());
        assertEquals(timing.intervals().count(), profile.intervals());
        assertEquals(20, profile.total(CpuSpriteFrameProfile.FILL));
        assertEquals(6, profile.total(CpuSpriteFrameProfile.STAGING));
        assertEquals(10, profile.total(CpuSpriteFrameProfile.OTHER));
        assertEquals(40, profile.total(CpuSpriteFrameProfile.OUTSIDE));
        long total = 0;
        for (int i = 0; i < CpuSpriteFrameProfile.NAMES.length; i++) total += profile.total(i);
        assertEquals(timing.intervals().totalNanos(), (double) total);
    }

    @Test
    void zeroWarmupWaitsForNextFrameAndPreservesUnroundedNanoseconds() {
        CpuSpriteFrameProfile profile = new CpuSpriteFrameProfile(0);
        profile.add(CpuSpriteFrameProfile.STAGING, 7);
        profile.recordFrame(0, 9);
        assertEquals(0, profile.intervals());
        profile.add(CpuSpriteFrameProfile.STAGING, 99);
        profile.recordFrame(20, 120);
        assertEquals(1, profile.intervals());
        assertEquals(7, profile.total(CpuSpriteFrameProfile.STAGING));
        assertEquals(2, profile.total(CpuSpriteFrameProfile.OTHER));
        assertEquals(11, profile.total(CpuSpriteFrameProfile.OUTSIDE));
    }
}
