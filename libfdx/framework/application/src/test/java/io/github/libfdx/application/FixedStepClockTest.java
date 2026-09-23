package io.github.libfdx.application;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class FixedStepClockTest {
    @Test
    void fixedInputSimulationIsEquivalentAcrossRenderCadences() {
        double reference = simulate(30);
        assertEquals(reference, simulate(60), 1e-10);
        assertEquals(reference, simulate(144), 1e-10);
    }
    private double simulate(int fps) {
        FixedStepClock clock = new FixedStepClock(1.0 / 120, 8);
        double velocity = 0, position = 0; int totalSteps = 0;
        for (int frame = 0; frame < fps * 10; frame++) {
            int steps = clock.advance(1.0 / fps); totalSteps += steps;
            for (int step = 0; step < steps; step++) {
                velocity += 2 * clock.stepSeconds(); position += velocity * clock.stepSeconds();
            }
            assertTrue(clock.alpha() >= 0 && clock.alpha() < 1);
        }
        assertEquals(1200, totalSteps);
        return position;
    }
    @Test
    void stallsAreBoundedAndPauseDoesNotLeakRemainder() {
        FixedStepClock clock = new FixedStepClock(.01, 4);
        assertEquals(0, clock.advance(.005)); assertEquals(.5, clock.alpha(), 1e-12);
        assertEquals(4, clock.advance(100)); assertEquals(99.96, clock.droppedSeconds(), 1e-12);
        assertEquals(.5, clock.alpha(), 1e-10);
        clock.pause(); assertEquals(0, clock.advance(10)); assertEquals(0, clock.alpha());
        clock.resume(); assertEquals(1, clock.advance(.01)); assertEquals(0, clock.alpha());
    }
    @Test
    void invalidTimingNeverMutatesClock() {
        FixedStepClock clock = new FixedStepClock(.01, 4); clock.advance(.005);
        assertThrows(IllegalArgumentException.class, () -> clock.advance(Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> clock.advance(-1));
        assertEquals(.5, clock.alpha());
        assertThrows(IllegalArgumentException.class, () -> new FixedStepClock(Double.MAX_VALUE, 4));
    }
}
