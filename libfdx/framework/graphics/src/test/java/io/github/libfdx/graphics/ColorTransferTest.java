package io.github.libfdx.graphics;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class ColorTransferTest {
    @Test
    void matchesStandardDarkToneAndMidGreyReferences() {
        assertEquals(.00313080495, ColorTransfer.srgbToLinear(.04045f), 5e-10);
        assertEquals(.002428216, ColorTransfer.srgbToLinear(8 / 255f), 1e-9);
        assertEquals(.2158605, ColorTransfer.srgbToLinear(128 / 255f), 4e-8);
        assertEquals(.73535698, ColorTransfer.linearToSrgb(.5f), 3e-8);
        assertEquals(1, ColorTransfer.linearToSrgb(1), 1e-7);
    }
    @Test
    void roundTripsEveryEightBitComponent() {
        for (int value = 0; value < 256; value++) {
            assertEquals(value, Math.round(ColorTransfer.linearToSrgb(ColorTransfer.srgbToLinear(value / 255f)) * 255));
        }
    }
}
