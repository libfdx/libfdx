package io.github.libfdx.tests.graphics;

import io.github.libfdx.graphics.ShapeRenderer;

/**
 * Runs the triangle test scenario.
 *
 * @author xpenatan
 */
public final class TriangleTest extends ShapeRenderTest {
    /**
     * Creates a triangle test.
     */
    public TriangleTest() {
        this(0L);
    }

    /**
     * Creates a triangle test.
     *
     * @param exitAfterFrames the exit after frames
     */
    public TriangleTest(long exitAfterFrames) {
        super("triangle", exitAfterFrames);
    }

    @Override
    void renderShape(ShapeRenderer shapes) {
        shapes.filledTriangle(0.0f, 0.65f, -0.65f, -0.55f, 0.65f, -0.55f,
                0.95f, 0.76f, 0.28f, 1.0f);
    }
}
