package io.github.libfdx.testsupport;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class AutoTestTimingTest {
    @Test
    void loadingNeverConsumesTheReadySceneObservationInterval() {
        var timing = new AutoTestTiming(6, 2, 2, 15);
        for (int frame = 0; frame < 120; frame++) {
            assertFalse(timing.update(1, false));
        }
        assertFalse(timing.loaded());
        for (int frame = 0; frame < 4; frame++) {
            assertFalse(timing.update(1, true));
        }
        assertTrue(timing.loaded());
        for (int frame = 0; frame < 5; frame++) {
            assertFalse(timing.update(1, true));
        }
        assertTrue(timing.update(1, true));
    }

    @Test
    void newLoadingRestartsObservationOfTheCompletedScene() {
        var timing = new AutoTestTiming(1, 1, 2, 15);
        for (int frame = 0; frame < 3; frame++) timing.update(1, true);
        assertTrue(timing.loaded());
        assertFalse(timing.update(1, false));
        assertFalse(timing.loaded());
        for (int frame = 0; frame < 3; frame++) assertFalse(timing.update(1, true));
        assertTrue(timing.update(1, true));
    }
}
