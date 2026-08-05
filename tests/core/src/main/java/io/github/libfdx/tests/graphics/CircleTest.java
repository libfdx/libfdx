package io.github.libfdx.tests.graphics;

import io.github.libfdx.graphics.ShapeRenderer;

/**
 * Runs the circle test scenario.
 *
 * @author xpenatan
 */
public final class CircleTest extends ShapeRenderTest {
    /**
     * Creates a circle test.
     */
    public CircleTest() {
        this(0L);
    }

    /**
     * Creates a circle test.
     *
     * @param exitAfterFrames the exit after frames
     */
    public CircleTest(long exitAfterFrames) {
        super("circle", exitAfterFrames);
    }

    @Override
    void renderShape(ShapeRenderer shapes) {
        shapes.filledCircle(0.0f, 0.0f, 0.62f, 64, 0.32f, 0.60f, 0.95f, 1.0f);
    }
}
