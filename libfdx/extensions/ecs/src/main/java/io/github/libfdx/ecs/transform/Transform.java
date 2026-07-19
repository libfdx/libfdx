package io.github.libfdx.ecs.transform;

import io.github.libfdx.math.Matrix4;
import io.github.libfdx.math.Quaternion;

/**
 * Mutable spatial data owned by an ECS transform component.
 *
 * <p>{@link #rotation} is the authoritative rotation. {@link #matrix} is
 * derived from position, rotation, and scale by {@link #updateMatrix()}.</p>
 */
public final class Transform {
    public float x;
    public float y;
    public float z;
    public final Quaternion rotation = new Quaternion();
    public float scaleX = 1.0f;
    public float scaleY = 1.0f;
    public float scaleZ = 1.0f;
    public final Matrix4 matrix = new Matrix4();

    public Transform() {
    }

    public Transform(float x, float y, float z) {
        this.x = x;
        this.y = y;
        this.z = z;
        updateMatrix();
    }

    /**
     * Normalizes the authoritative rotation and updates the derived local TRS matrix.
     *
     * @return this transform for chaining
     */
    public Transform updateMatrix() {
        rotation.normalize();
        matrix.setToTrs(
            x, y, z,
            rotation.x(), rotation.y(), rotation.z(), rotation.w(),
            scaleX, scaleY, scaleZ
        );
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
        return updateMatrix();
    }

    public Transform copy() {
        return new Transform().set(this);
    }
}
