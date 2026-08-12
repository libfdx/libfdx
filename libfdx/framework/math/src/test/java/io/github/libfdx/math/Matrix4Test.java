package io.github.libfdx.math;

import io.github.libfdx.core.FdxException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class Matrix4Test {
    private static final float EPSILON = 0.0001f;

    @Test
    void invertsProjectionViewMatrix() {
        Matrix4 projection = Matrix4.perspective(67.0f, 16.0f / 9.0f, 0.1f, 100.0f);
        Matrix4 view = Matrix4.lookAt(
                new Vector3(2.0f, 3.0f, 7.0f),
                new Vector3(-1.0f, 0.5f, -2.0f),
                new Vector3(0.0f, 1.0f, 0.0f));
        Matrix4 combined = new Matrix4().setToMul(projection, view);

        Matrix4 identity = new Matrix4(combined).mul(new Matrix4(combined).invert());

        float[] values = identity.values();
        for (int column = 0; column < 4; column++) {
            for (int row = 0; row < 4; row++) {
                assertEquals(column == row ? 1.0f : 0.0f, values[column * 4 + row], EPSILON);
            }
        }
    }

    @Test
    void projectiveTransformSupportsAliasedOutput() {
        Matrix4 projection = Matrix4.perspective(90.0f, 1.0f, 1.0f, 100.0f);
        Vector3 position = new Vector3(1.0f, 0.0f, -2.0f);

        Vector3 result = projection.transformProjective(position, position);

        assertSame(position, result);
        assertEquals(0.5f, result.x(), EPSILON);
        assertEquals(0.0f, result.y(), EPSILON);
        assertEquals(1.0f / 99.0f, result.z(), EPSILON);
    }

    @Test
    void rejectsSingularInverseAndZeroProjectiveW() {
        assertThrows(FdxException.class, () -> Matrix4.scale(1.0f, 0.0f, 1.0f).invert());
        assertThrows(FdxException.class,
                () -> Matrix4.perspective(90.0f, 1.0f, 1.0f, 100.0f)
                        .transformProjective(new Vector3(0.0f, 0.0f, 0.0f)));
    }
}
