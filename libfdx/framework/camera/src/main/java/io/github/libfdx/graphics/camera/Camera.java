package io.github.libfdx.graphics.camera;

import io.github.libfdx.core.FdxException;
import io.github.libfdx.math.Matrix4;
import io.github.libfdx.math.Vector3;

/**
 * Represents a camera.
 *
 * @author xpenatan
 */
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

    /**
     * Sets the projection and returns this camera.
     *
     * @param projection the projection
     * @return this camera for chaining
     */
    public Camera projection(CameraProjection projection) {
        if (projection == null) {
            throw new FdxException("Camera projection cannot be null");
        }
        this.projection = projection;
        return this;
    }

    /**
     * Returns the projection.
     *
     * @return the projection
     */
    public CameraProjection projection() {
        return projection;
    }

    /**
     * Sets the viewport and returns this camera.
     *
     * @param width the width in pixels
     * @param height the height in pixels
     * @return this camera for chaining
     */
    public Camera viewport(float width, float height) {
        if (width <= 0.0f || height <= 0.0f) {
            throw new FdxException("Camera viewport dimensions must be greater than zero");
        }
        viewportWidth = width;
        viewportHeight = height;
        return this;
    }

    /**
     * Sets the field of view and returns this camera.
     *
     * @param fieldOfViewDegrees the field of view degrees
     * @return this camera for chaining
     */
    public Camera fieldOfView(float fieldOfViewDegrees) {
        if (fieldOfViewDegrees <= 0.0f) {
            throw new FdxException("Camera field of view must be greater than zero");
        }
        this.fieldOfViewDegrees = fieldOfViewDegrees;
        return this;
    }

    /**
     * Sets the near far and returns this camera.
     *
     * @param near the near
     * @param far the far
     * @return this camera for chaining
     */
    public Camera nearFar(float near, float far) {
        if (near <= 0.0f || far <= near) {
            throw new FdxException("Camera near/far range is invalid");
        }
        this.near = near;
        this.far = far;
        return this;
    }

    /**
     * Sets the zoom and returns this camera.
     *
     * @param zoom the zoom
     * @return this camera for chaining
     */
    public Camera zoom(float zoom) {
        if (zoom <= 0.0f) {
            throw new FdxException("Camera zoom must be greater than zero");
        }
        this.zoom = zoom;
        return this;
    }

    /**
     * Sets the position and returns this camera.
     *
     * @param x the x coordinate
     * @param y the y coordinate
     * @param z the z coordinate
     * @return this camera for chaining
     */
    public Camera position(float x, float y, float z) {
        position.set(x, y, z);
        return this;
    }

    /**
     * Sets the direction and returns this camera.
     *
     * @param x the x coordinate
     * @param y the y coordinate
     * @param z the z coordinate
     * @return this camera for chaining
     */
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

    /**
     * Sets the look at and returns this camera.
     *
     * @param x the x coordinate
     * @param y the y coordinate
     * @param z the z coordinate
     * @return this camera for chaining
     */
    public Camera lookAt(float x, float y, float z) {
        target.set(x, y, z);
        return direction(x - position.x(), y - position.y(), z - position.z());
    }

    /**
     * Sets the up and returns this camera.
     *
     * @param x the x coordinate
     * @param y the y coordinate
     * @param z the z coordinate
     * @return this camera for chaining
     */
    public Camera up(float x, float y, float z) {
        float len = (float)Math.sqrt(x * x + y * y + z * z);
        if (len == 0.0f) {
            throw new FdxException("Camera up vector cannot be zero length");
        }
        float invLen = 1.0f / len;
        up.set(x * invLen, y * invLen, z * invLen);
        return this;
    }

    /**
     * Updates this instance.
     *
     * @return this camera for chaining
     */
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

    /**
     * Returns the position.
     *
     * @return the position
     */
    public Vector3 position() {
        return position;
    }

    /**
     * Returns the direction.
     *
     * @return the direction
     */
    public Vector3 direction() {
        return direction;
    }

    /**
     * Returns the up.
     *
     * @return the up
     */
    public Vector3 up() {
        return up;
    }

    /**
     * Returns the projection matrix.
     *
     * @return the projection matrix
     */
    public Matrix4 projectionMatrix() {
        update();
        return projectionMatrix;
    }

    /**
     * Returns the view.
     *
     * @return the view
     */
    public Matrix4 view() {
        update();
        return viewMatrix;
    }

    /**
     * Returns the combined.
     *
     * @return the combined
     */
    public Matrix4 combined() {
        update();
        return combinedMatrix;
    }

    /**
     * Returns the near.
     *
     * @return the near
     */
    public float near() {
        return near;
    }

    /**
     * Returns the far.
     *
     * @return the far
     */
    public float far() {
        return far;
    }

    /**
     * Returns the viewport width.
     *
     * @return the viewport width
     */
    public float viewportWidth() {
        return viewportWidth;
    }

    /**
     * Returns the viewport height.
     *
     * @return the viewport height
     */
    public float viewportHeight() {
        return viewportHeight;
    }

    /**
     * Returns the field of view.
     *
     * @return the field of view
     */
    public float fieldOfView() {
        return fieldOfViewDegrees;
    }

    /**
     * Returns the zoom.
     *
     * @return the zoom
     */
    public float zoom() {
        return zoom;
    }
}
