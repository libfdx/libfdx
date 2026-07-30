package io.github.libfdx.samples.g2d.platformer.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

final class PlatformerRendererTest {
    @Test
    void squareViewportPreservesWorldFramingAcrossTargetAspectRatios() {
        assertViewport(960, 540, 540, 210, 0);
        assertViewport(540, 960, 540, 0, 210);
        assertViewport(640, 640, 640, 0, 0);
    }

    @Test
    void squareViewportRejectsInvalidTargetDimensions() {
        assertThrows(IllegalArgumentException.class,
                () -> PlatformerRenderer.squareViewportSize(0, 540));
        assertThrows(IllegalArgumentException.class,
                () -> PlatformerRenderer.squareViewportSize(960, 0));
    }

    private static void assertViewport(
            int width,
            int height,
            int expectedSize,
            int expectedX,
            int expectedY) {
        int size = PlatformerRenderer.squareViewportSize(width, height);
        assertEquals(expectedSize, size);
        assertEquals(expectedX, PlatformerRenderer.centeredViewportOffset(width, size));
        assertEquals(expectedY, PlatformerRenderer.centeredViewportOffset(height, size));
    }
}
