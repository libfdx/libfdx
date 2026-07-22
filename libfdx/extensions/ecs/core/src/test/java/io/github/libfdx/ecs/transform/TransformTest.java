package io.github.libfdx.ecs.transform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class TransformTest {
    private static final float EPSILON = 0.00001f;

    @Test
    void defaultsToIdentityRotationScaleAndMatrix() {
        Transform transform = new Transform();
        float[] matrix = transform.matrix().values();

        assertEquals(0.0f, transform.rotation().x());
        assertEquals(0.0f, transform.rotation().y());
        assertEquals(0.0f, transform.rotation().z());
        assertEquals(1.0f, transform.rotation().w());
        assertEquals(1.0f, transform.scaleX());
        assertEquals(1.0f, transform.scaleY());
        assertEquals(1.0f, transform.scaleZ());
        assertEquals(1.0f, matrix[0]);
        assertEquals(1.0f, matrix[5]);
        assertEquals(1.0f, matrix[10]);
        assertEquals(1.0f, matrix[15]);
    }

    @Test
    void updateMatrixNormalizesQuaternionAndBuildsLocalTrs() {
        Transform transform = new Transform();
        transform.position(3.0f, 4.0f, 5.0f)
                .scale(2.0f, 3.0f, 4.0f)
                .rotation(0.0f, 0.0f, 2.0f, 2.0f);

        transform.updateMatrix();

        float halfSqrtTwo = (float)Math.sqrt(0.5);
        float[] matrix = transform.matrix().values();
        assertEquals(halfSqrtTwo, transform.rotation().z(), EPSILON);
        assertEquals(halfSqrtTwo, transform.rotation().w(), EPSILON);
        assertEquals(0.0f, matrix[0], EPSILON);
        assertEquals(2.0f, matrix[1], EPSILON);
        assertEquals(-3.0f, matrix[4], EPSILON);
        assertEquals(0.0f, matrix[5], EPSILON);
        assertEquals(4.0f, matrix[10], EPSILON);
        assertEquals(3.0f, matrix[12], EPSILON);
        assertEquals(4.0f, matrix[13], EPSILON);
        assertEquals(5.0f, matrix[14], EPSILON);
    }

    @Test
    void copyOwnsIndependentQuaternionAndMatrix() {
        Transform source = new Transform(1.0f, 2.0f, 3.0f);
        source.rotation().set(0.1f, 0.2f, 0.3f, 0.4f);
        source.scale(2.0f, 1.0f, 1.0f);
        source.updateMatrix();

        Transform copy = source.copy();

        assertNotSame(source.rotation(), copy.rotation());
        assertNotSame(source.matrix(), copy.matrix());
        assertEquals(source.rotation().x(), copy.rotation().x(), EPSILON);
        assertEquals(source.rotation().y(), copy.rotation().y(), EPSILON);
        assertEquals(source.rotation().z(), copy.rotation().z(), EPSILON);
        assertEquals(source.rotation().w(), copy.rotation().w(), EPSILON);
        assertEquals(source.matrix().values()[0], copy.matrix().values()[0], EPSILON);

        source.rotation().idt();
        source.position(9.0f, source.y(), source.z());

        assertEquals(1.0f, copy.x());
        assertEquals(1.0f, copy.matrix().values()[12]);
    }

    @Test
    void rebuildsMatrixOnlyAfterTrsChanges() {
        Transform transform = new Transform();
        assertFalse(transform.matrixDirty());

        transform.position(2.0f, 3.0f, 4.0f);
        assertTrue(transform.matrixDirty());
        assertSame(transform.matrix(), transform.matrix());
        assertFalse(transform.matrixDirty());

        transform.position(2.0f, 3.0f, 4.0f);
        transform.scale(1.0f, 1.0f, 1.0f);
        assertFalse(transform.matrixDirty());

        transform.rotation().set(0.0f, 0.0f, 1.0f, 1.0f);
        assertTrue(transform.matrixDirty());
        transform.updateMatrix();
        assertFalse(transform.matrixDirty());
    }
}
