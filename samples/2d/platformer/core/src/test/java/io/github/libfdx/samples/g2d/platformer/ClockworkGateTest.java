package io.github.libfdx.samples.g2d.platformer;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class ClockworkGateTest {
    @Test
    void bellsRecoverFromWrongOrderAndLatchWhenSolved() {
        var gate = new ClockworkGate();
        gate.mechanism(true, 231, "II first, I last", 2, 0);
        gate.turn(1);
        gate.turn(0);
        assertTrue(gate.mistake());
        assertEquals(0, gate.progress());
        gate.turn(1);
        gate.turn(1);
        assertEquals(0, gate.progress());
        gate.turn(1);
        assertEquals(1, gate.progress());
        gate.turn(2);
        assertFalse(gate.solved());
        gate.turn(0);
        assertTrue(gate.solved());
        assertEquals(0, gate.opening());
        gate.update(.25f);
        assertTrue(gate.opening() > 0 && gate.opening() < 1);
        gate.turn(2);
        gate.update(1);
        assertEquals(1, gate.opening());
        gate.reset();
        assertFalse(gate.solved());
        assertEquals(0, gate.progress());
    }

    @Test
    void linkedLampsCanBeUndoneAndResetToTheAuthoredPattern() {
        var gate = new ClockworkGate();
        gate.mechanism(false, 5, "Light all three", 2, 0);
        gate.turn(0);
        assertEquals(6, gate.lamps());
        gate.turn(0);
        assertEquals(5, gate.lamps());
        gate.turn(1);
        gate.turn(2);
        assertTrue(gate.solved());
        gate.reset();
        assertEquals(5, gate.lamps());
        assertFalse(gate.solved());
    }

}
