package io.github.libfdx.ecs.transform;

import io.github.libfdx.math.Matrix4;
import io.github.libfdx.math.Quaternion;

/**
 * Mutable spatial data owned by an ECS transform component.
 *
 * <p>The quaternion returned by {@link #rotation()} is authoritative. The
 * matrix returned by {@link #matrix()} is derived lazily from position,
 * rotation, and scale and is reused while those values remain unchanged.</p>
 */
public final class Transform {
    private float x;
    private float y;
    private float z;
    private final Quaternion rotation = new Quaternion();
    private float scaleX = 1.0f;
    private float scaleY = 1.0f;
    private float scaleZ = 1.0f;
    private final Matrix4 matrix = new Matrix4();
    private boolean matrixDirty;
    private int matrixRotationX = Float.floatToIntBits(0.0f);
    private int matrixRotationY = Float.floatToIntBits(0.0f);
    private int matrixRotationZ = Float.floatToIntBits(0.0f);
    private int matrixRotationW = Float.floatToIntBits(1.0f);

    public Transform() {
    }

    public Transform(float x, float y, float z) {
        position(x, y, z);
    }

    public float x() {
        return x;
    }

    public float y() {
        return y;
    }

    public float z() {
        return z;
    }

    public Quaternion rotation() {
        return rotation;
    }

    public float scaleX() {
        return scaleX;
    }

    public float scaleY() {
        return scaleY;
    }

    public float scaleZ() {
        return scaleZ;
    }

    public Transform position(float x, float y, float z) {
        if (!same(this.x, x) || !same(this.y, y) || !same(this.z, z)) {
            this.x = x;
            this.y = y;
            this.z = z;
            matrixDirty = true;
        }
        return this;
    }

    public Transform translate(float x, float y, float z) {
        if (x != 0.0f || y != 0.0f || z != 0.0f) {
            this.x += x;
            this.y += y;
            this.z += z;
            matrixDirty = true;
        }
        return this;
    }

    public Transform rotation(float x, float y, float z, float w) {
        rotation.set(x, y, z, w);
        return this;
    }

    public Transform scale(float x, float y, float z) {
        if (!same(scaleX, x) || !same(scaleY, y) || !same(scaleZ, z)) {
            scaleX = x;
            scaleY = y;
            scaleZ = z;
            matrixDirty = true;
        }
        return this;
    }

    /** Returns whether the derived matrix needs to be rebuilt. */
    public boolean matrixDirty() {
        return matrixDirty || rotationChanged();
    }

    /** Returns the reused local TRS matrix, rebuilding it only when necessary. */
    public Matrix4 matrix() {
        updateMatrix();
        return matrix;
    }

    /**
     * Ensures the derived local TRS matrix is current. Unchanged transforms are a no-op.
     *
     * @return this transform for chaining
     */
    public Transform updateMatrix() {
        if (!matrixDirty()) {
            return this;
        }
        rotation.normalize();
        matrix.setToTrs(
            x, y, z,
            rotation.x(), rotation.y(), rotation.z(), rotation.w(),
            scaleX, scaleY, scaleZ
        );
        matrixRotationX = Float.floatToIntBits(rotation.x());
        matrixRotationY = Float.floatToIntBits(rotation.y());
        matrixRotationZ = Float.floatToIntBits(rotation.z());
        matrixRotationW = Float.floatToIntBits(rotation.w());
        matrixDirty = false;
        return this;
    }

    public Transform set(Transform other) {
        if (other == null) {
            throw new IllegalArgumentException("other cannot be null.");
        }
        x = other.x;
        y = other.y;
        z = other.z;
        rotation.set(other.rotation.x(), other.rotation.y(), other.rotation.z(), other.rotation.w());
        scaleX = other.scaleX;
        scaleY = other.scaleY;
        scaleZ = other.scaleZ;
        matrixDirty = true;
        return updateMatrix();
    }

    public Transform copy() {
        return new Transform().set(this);
    }

    private boolean rotationChanged() {
        return matrixRotationX != Float.floatToIntBits(rotation.x())
                || matrixRotationY != Float.floatToIntBits(rotation.y())
                || matrixRotationZ != Float.floatToIntBits(rotation.z())
                || matrixRotationW != Float.floatToIntBits(rotation.w());
    }

    private static boolean same(float first, float second) {
        return Float.floatToIntBits(first) == Float.floatToIntBits(second);
    }
}
