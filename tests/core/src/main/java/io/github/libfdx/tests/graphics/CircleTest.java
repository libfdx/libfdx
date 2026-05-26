package io.github.libfdx.tests.graphics;

import io.github.libfdx.graphics.g2d.ShapeRenderer2D;

public final class CircleTest extends ShapeRenderTest {
    public CircleTest() {
        this(0L);
    }

    public CircleTest(long exitAfterFrames) {
        super("circle", exitAfterFrames);
    }

    @Override
    void renderShape(ShapeRenderer2D shapes) {
        shapes.filledCircle(0.0f, 0.0f, 0.62f, 64, 0.32f, 0.60f, 0.95f, 1.0f);
    }
}
