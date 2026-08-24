package io.github.libfdx.math;

import io.github.libfdx.core.FdxException;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class Matrix4Test {
    private static final float EPSILON = 0.0001f;

    @Test
    void multiplyMutatesAndReturnsSameMatrix() {
        Matrix4 matrix = new Matrix4().setToTranslation(2.0f, 3.0f, 4.0f);
        Matrix4 rotation = new Matrix4().setToRotationY(0.42f);
        Matrix4 expected = new Matrix4(matrix).mul(rotation);

        Matrix4 result = matrix.multiply(rotation);

        assertSame(matrix, result);
        float[] expectedValues = expected.values();
        float[] actualValues = matrix.values();
        for (int i = 0; i < Matrix4.VALUE_COUNT; i++) {
            assertEquals(expectedValues[i], actualValues[i], EPSILON);
        }
    }

    @Test
    void compositionMutatesOneCallerOwnedMatrix() {
        float angle = 0.42f;
        Matrix4 matrix = new Matrix4();

        Matrix4 result = matrix.setToTranslation(2.0f, 3.0f, 4.0f)
                .rotateY(angle)
                .scale(1.5f, 0.5f, 2.0f);

        Matrix4 expected = new Matrix4().setToTrs(2.0f, 3.0f, 4.0f,
                0.0f, (float)Math.sin(angle * 0.5f), 0.0f,
                (float)Math.cos(angle * 0.5f), 1.5f, 0.5f, 2.0f);
        assertSame(matrix, result);
        assertMatrixEquals(expected, matrix);
    }

    @Test
    void allocationFreeCompositionMatchesExplicitMultiplication() {
        Matrix4 base = new Matrix4().setToTrs(2.0f, -3.0f, 4.0f,
                0.2f, -0.3f, 0.4f, 0.8f, 1.2f, 0.7f, -1.1f);
        Matrix4 expected = new Matrix4(base)
                .mul(new Matrix4().setToTranslation(-1.0f, 5.0f, 2.0f))
                .mul(new Matrix4().setToRotationX(0.17f))
                .mul(new Matrix4().setToRotationY(-0.31f))
                .mul(new Matrix4().setToRotationZ(0.63f))
                .mul(new Matrix4().setToRotation(1.0f, 2.0f, -3.0f,
                        0.28f))
                .mul(new Matrix4().setToRotationQuaternion(
                        -0.1f, 0.35f, 0.2f, 0.9f))
                .mul(new Matrix4().setToScale(0.75f, -1.5f, 2.25f));
        Matrix4 actual = new Matrix4(base);

        Matrix4 result = actual.translate(-1.0f, 5.0f, 2.0f)
                .rotateX(0.17f)
                .rotateY(-0.31f)
                .rotateZ(0.63f)
                .rotate(1.0f, 2.0f, -3.0f, 0.28f)
                .rotateQuaternion(-0.1f, 0.35f, 0.2f, 0.9f)
                .scale(0.75f, -1.5f, 2.25f);

        assertSame(actual, result);
        assertMatrixEquals(expected, actual);
    }

    @Test
    void exposesNoAllocatingStaticMatrixFactories() {
        for (Method method : Matrix4.class.getDeclaredMethods()) {
            assertFalse(Modifier.isStatic(method.getModifiers())
                            && method.getReturnType() == Matrix4.class,
                    () -> "Matrix4 factory must use caller-owned output: "
                            + method.getName());
        }
    }

    @Test
    void invertsProjectionViewMatrix() {
        Matrix4 projection = new Matrix4().setToPerspective(
                67.0f, 16.0f / 9.0f, 0.1f, 100.0f);
        Matrix4 view = new Matrix4().setToLookAt(
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
        Matrix4 projection = new Matrix4().setToPerspective(
                90.0f, 1.0f, 1.0f, 100.0f);
        Vector3 position = new Vector3(1.0f, 0.0f, -2.0f);

        Vector3 result = projection.transformProjective(position, position);

        assertSame(position, result);
        assertEquals(0.5f, result.x(), EPSILON);
        assertEquals(0.0f, result.y(), EPSILON);
        assertEquals(1.0f / 99.0f, result.z(), EPSILON);
    }

    @Test
    void rejectsSingularInverseAndZeroProjectiveW() {
        assertThrows(FdxException.class,
                () -> new Matrix4().setToScale(1.0f, 0.0f, 1.0f).invert());
        assertThrows(FdxException.class,
                () -> new Matrix4().setToPerspective(90.0f, 1.0f, 1.0f, 100.0f)
                        .transformProjective(new Vector3(0.0f, 0.0f, 0.0f)));
    }

    private static void assertMatrixEquals(Matrix4 expected, Matrix4 actual) {
        float[] expectedValues = expected.values();
        float[] actualValues = actual.values();
        for (int i = 0; i < Matrix4.VALUE_COUNT; i++) {
            assertEquals(expectedValues[i], actualValues[i], EPSILON);
        }
    }
}
