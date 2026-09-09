package io.github.libfdx.graphics.g2d;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class PixelArtViewportTest {
    @Test void integerFitCentersMarginsAndMapsInputBackToWorld() {
        PixelArtViewport v = new PixelArtViewport(160, 120).update(650, 490);
        assertEquals(4, v.scale()); assertEquals(5, v.x()); assertEquals(5, v.y());
        assertEquals(-1 + 10f / 650, v.clipX(0));
        assertEquals(20, v.worldX(85)); assertEquals(10, v.worldY(45));
        assertFalse(v.contains(4, 10)); assertTrue(v.contains(5, 5)); assertFalse(v.contains(645, 5));
    }
    @Test void smallFramebufferCropsAtOneTimesAndOddMarginsHaveStablePlacement() {
        PixelArtViewport v = new PixelArtViewport(160, 120).update(99, 99);
        assertEquals(1, v.scale()); assertEquals(-31, v.x()); assertEquals(-11, v.y());
        assertEquals(31, v.worldX(0)); assertEquals(11, v.worldY(0));
        assertTrue(v.contains(0, 0)); assertFalse(v.contains(99, 0));
    }
    @Test void snappingIsStableForNegativeCoordinatesAndDoesNotChangeSimulationValues() {
        PixelArtViewport v = new PixelArtViewport(160, 120).camera(.49f, -.49f);
        assertEquals(v.clipX(10), v.clipX(10.49f));
        assertEquals(v.clipY(-10), v.clipY(-10.49f));
        v.snap(false);
        assertNotEquals(v.clipX(10), v.clipX(10.49f));
        assertEquals(.49f, v.worldX(0), 1e-6);
    }
    @Test void validatesDimensionsAndAvoidsOverflowForExtremeSizes() {
        assertThrows(IllegalArgumentException.class, () -> new PixelArtViewport(0, 1));
        PixelArtViewport v = new PixelArtViewport(Integer.MAX_VALUE, Integer.MAX_VALUE).update(1, 1);
        assertEquals(1, v.scale()); assertTrue(v.contains(0, 0));
        assertThrows(IllegalArgumentException.class, () -> v.update(0, 0));
        assertThrows(IllegalArgumentException.class, () -> v.camera(Float.NaN, 0));
    }
}
