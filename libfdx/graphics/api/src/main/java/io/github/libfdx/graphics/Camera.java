package io.github.libfdx.graphics;

import io.github.libfdx.core.FdxException;
import io.github.libfdx.math.Matrix4;
import io.github.libfdx.math.Vector3;

public final class Camera {
    private final Vector3 position = new Vector3(0.0f, 0.0f, 1.0f);
    private final Vector3 direction = new Vector3(0.0f, 0.0f, -1.0f);
    private final Vector3 up = new Vector3(0.0f, 1.0f, 0.0f);
    private final Vector3 target = new Vector3(0.0f, 0.0f, 0.0f);
    private final Matrix4 projectionMatrix = new Matrix4();
    private final Matrix4 viewMatrix = new Matrix4();
    private final Matrix4 combinedMatrix = new Matrix4();
    private CameraProjection projection = CameraProjection.ORTHOGRAPHIC;
    private float viewportWidth = 1.0f;
    private float viewportHeight = 1.0f;
    private float fieldOfViewDegrees = 67.0f;
    private float near = 0.1f;
    private float far = 100.0f;
    private float zoom = 1.0f;

    public Camera projection(CameraProjection projection) {
        if (projection == null) {
            throw new FdxException("Camera projection cannot be null");
        }
        this.projection = projection;
        return this;
    }

    public CameraProjection projection() {
        return projection;
    }

    public Camera viewport(float width, float height) {
        if (width <= 0.0f || height <= 0.0f) {
            throw new FdxException("Camera viewport dimensions must be greater than zero");
        }
        viewportWidth = width;
        viewportHeight = height;
        return this;
    }

    public Camera fieldOfView(float fieldOfViewDegrees) {
        if (fieldOfViewDegrees <= 0.0f) {
            throw new FdxException("Camera field of view must be greater than zero");
        }
        this.fieldOfViewDegrees = fieldOfViewDegrees;
        return this;
    }

    public Camera nearFar(float near, float far) {
        if (near <= 0.0f || far <= near) {
            throw new FdxException("Camera near/far range is invalid");
        }
        this.near = near;
        this.far = far;
        return this;
    }

    public Camera zoom(float zoom) {
        if (zoom <= 0.0f) {
            throw new FdxException("Camera zoom must be greater than zero");
        }
        this.zoom = zoom;
        return this;
    }

    public Camera position(float x, float y, float z) {
        position.set(x, y, z);
        return this;
    }

    public Camera direction(float x, float y, float z) {
        float len = (float)Math.sqrt(x * x + y * y + z * z);
        if (len == 0.0f) {
            throw new FdxException("Camera direction cannot be zero length");
        }
        float invLen = 1.0f / len;
        direction.set(x * invLen, y * invLen, z * invLen);
        target.set(position.x() + direction.x(), position.y() + direction.y(), position.z() + direction.z());
        return this;
    }

    public Camera lookAt(float x, float y, float z) {
        target.set(x, y, z);
        return direction(x - position.x(), y - position.y(), z - position.z());
    }

    public Camera up(float x, float y, float z) {
        float len = (float)Math.sqrt(x * x + y * y + z * z);
        if (len == 0.0f) {
            throw new FdxException("Camera up vector cannot be zero length");
        }
        float invLen = 1.0f / len;
        up.set(x * invLen, y * invLen, z * invLen);
        return this;
    }

    public Camera update() {
        if (projection == CameraProjection.PERSPECTIVE) {
            projectionMatrix.setToPerspective(fieldOfViewDegrees, viewportWidth / viewportHeight, near, far);
        }
        else {
            float width = viewportWidth * zoom;
            float height = viewportHeight * zoom;
            projectionMatrix.setToOrthographic(-width * 0.5f, width * 0.5f, -height * 0.5f, height * 0.5f, near, far);
        }
        target.set(position.x() + direction.x(), position.y() + direction.y(), position.z() + direction.z());
        viewMatrix.setToLookAt(position, target, up);
        combinedMatrix.setToMul(projectionMatrix, viewMatrix);
        return this;
    }

    public Vector3 position() {
        return position;
    }

    public Vector3 direction() {
        return direction;
    }

    public Vector3 up() {
        return up;
    }

    public Matrix4 projectionMatrix() {
        update();
        return projectionMatrix;
    }

    public Matrix4 view() {
        update();
        return viewMatrix;
    }

    public Matrix4 combined() {
        update();
        return combinedMatrix;
    }

    public float near() {
        return near;
    }

    public float far() {
        return far;
    }

    public float viewportWidth() {
        return viewportWidth;
    }

    public float viewportHeight() {
        return viewportHeight;
    }

    public float fieldOfView() {
        return fieldOfViewDegrees;
    }

    public float zoom() {
        return zoom;
    }
}
