package io.github.libfdx.tests.graphics;

import io.github.libfdx.graphics.g2d.ShapeRenderer2D;

/**
 * Runs the square test scenario.
 *
 * @author xpenatan
 */
public final class SquareTest extends ShapeRenderTest {
    /**
     * Creates a square test.
     */
    public SquareTest() {
        this(0L);
    }

    /**
     * Creates a square test.
     *
     * @param exitAfterFrames the exit after frames
     */
    public SquareTest(long exitAfterFrames) {
        super("square", exitAfterFrames);
    }

    @Override
    void renderShape(ShapeRenderer2D shapes) {
        shapes.filledRect(-0.55f, -0.55f, 1.10f, 1.10f, 0.20f, 0.78f, 0.48f, 1.0f);
    }
}
