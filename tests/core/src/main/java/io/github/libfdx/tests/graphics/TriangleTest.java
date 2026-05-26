package io.github.libfdx.tests.graphics;

import io.github.libfdx.graphics.g2d.ShapeRenderer2D;

public final class TriangleTest extends ShapeRenderTest {
    public TriangleTest() {
        this(0L);
    }

    public TriangleTest(long exitAfterFrames) {
        super("triangle", exitAfterFrames);
    }

    @Override
    void renderShape(ShapeRenderer2D shapes) {
        shapes.filledTriangle(0.0f, 0.65f, -0.65f, -0.55f, 0.65f, -0.55f,
                0.95f, 0.76f, 0.28f, 1.0f);
    }
}
