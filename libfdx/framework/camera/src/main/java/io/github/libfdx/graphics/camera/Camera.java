package io.github.libfdx.graphics.camera;

import io.github.libfdx.core.FdxException;
import io.github.libfdx.math.Matrix4;
import io.github.libfdx.math.Ray;
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
    private final Matrix4 inverseCombinedMatrix = new Matrix4();
    private final Vector3 pickNear = new Vector3();
    private final Vector3 pickFar = new Vector3();
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
        inverseCombinedMatrix.set(combinedMatrix).invert();
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

    /**
     * Projects a world position into a top-left-origin screen coordinate. The output x and y coordinates use the same
     * units as this camera's viewport, and its z coordinate maps the near and far planes to zero and one respectively.
     *
     * @param worldCoordinates the world position
     * @param out the output screen coordinate, which may be the input vector
     * @return the output vector
     */
    public Vector3 project(Vector3 worldCoordinates, Vector3 out) {
        return project(worldCoordinates, 0.0f, 0.0f, viewportWidth, viewportHeight, out);
    }

    /**
     * Projects a world position into a top-left-origin screen viewport. Screen viewport values may use logical pixels
     * even when the camera viewport uses physical framebuffer pixels. The output z coordinate maps the near and far
     * planes to zero and one respectively.
     *
     * @param worldCoordinates the world position
     * @param screenViewportX the top-left screen viewport x coordinate
     * @param screenViewportY the top-left screen viewport y coordinate
     * @param screenViewportWidth the screen viewport width
     * @param screenViewportHeight the screen viewport height
     * @param out the output screen coordinate, which may be the input vector
     * @return the output vector
     */
    public Vector3 project(Vector3 worldCoordinates,
            float screenViewportX, float screenViewportY,
            float screenViewportWidth, float screenViewportHeight, Vector3 out) {
        validateProjectionArguments(worldCoordinates, out, screenViewportWidth, screenViewportHeight);
        update();
        combinedMatrix.transformProjective(worldCoordinates, out);
        return out.set(
                screenViewportX + (out.x() + 1.0f) * screenViewportWidth * 0.5f,
                screenViewportY + (1.0f - out.y()) * screenViewportHeight * 0.5f,
                (out.z() + 1.0f) * 0.5f);
    }

    /**
     * Unprojects a top-left-origin screen coordinate into a world position. The input x and y coordinates must use the
     * same units as this camera's viewport. A z coordinate of zero selects the near plane, and one selects the far plane.
     *
     * @param screenCoordinates the screen coordinate
     * @param out the output world position, which may be the input vector
     * @return the output vector
     */
    public Vector3 unproject(Vector3 screenCoordinates, Vector3 out) {
        return unproject(screenCoordinates, 0.0f, 0.0f, viewportWidth, viewportHeight, out);
    }

    /**
     * Unprojects a top-left-origin screen coordinate from a screen viewport into a world position. Screen viewport
     * values may use logical pixels even when the camera viewport uses physical framebuffer pixels. A z coordinate of
     * zero selects the near plane, and one selects the far plane.
     *
     * @param screenCoordinates the screen coordinate
     * @param screenViewportX the top-left screen viewport x coordinate
     * @param screenViewportY the top-left screen viewport y coordinate
     * @param screenViewportWidth the screen viewport width
     * @param screenViewportHeight the screen viewport height
     * @param out the output world position, which may be the input vector
     * @return the output vector
     */
    public Vector3 unproject(Vector3 screenCoordinates,
            float screenViewportX, float screenViewportY,
            float screenViewportWidth, float screenViewportHeight, Vector3 out) {
        validateProjectionArguments(screenCoordinates, out, screenViewportWidth, screenViewportHeight);
        float normalizedX = (screenCoordinates.x() - screenViewportX) * 2.0f / screenViewportWidth - 1.0f;
        float normalizedY = 1.0f - (screenCoordinates.y() - screenViewportY) * 2.0f / screenViewportHeight;
        float normalizedZ = screenCoordinates.z() * 2.0f - 1.0f;
        update();
        return unprojectUpdated(normalizedX, normalizedY, normalizedZ, out);
    }

    private Vector3 unprojectUpdated(float normalizedX, float normalizedY, float normalizedZ, Vector3 out) {
        out.set(normalizedX, normalizedY, normalizedZ);
        return inverseCombinedMatrix.transformProjective(out, out);
    }

    /**
     * Creates a pick ray for a top-left-origin screen coordinate. The coordinate must use the same units as this
     * camera's viewport.
     *
     * @param screenX the screen x coordinate
     * @param screenY the screen y coordinate
     * @return a new pick ray
     */
    public Ray getPickRay(float screenX, float screenY) {
        return getPickRay(screenX, screenY, new Ray());
    }

    /**
     * Writes a pick ray for a top-left-origin screen coordinate. The coordinate must use the same units as this
     * camera's viewport.
     *
     * @param screenX the screen x coordinate
     * @param screenY the screen y coordinate
     * @param out the output ray
     * @return the output ray
     */
    public Ray getPickRay(float screenX, float screenY, Ray out) {
        return getPickRay(screenX, screenY, 0.0f, 0.0f, viewportWidth, viewportHeight, out);
    }

    /**
     * Creates a pick ray for a top-left-origin screen coordinate and screen viewport. Screen viewport values may use
     * logical pixels even when the camera viewport uses physical framebuffer pixels.
     *
     * @param screenX the screen x coordinate
     * @param screenY the screen y coordinate
     * @param screenViewportX the top-left screen viewport x coordinate
     * @param screenViewportY the top-left screen viewport y coordinate
     * @param screenViewportWidth the screen viewport width
     * @param screenViewportHeight the screen viewport height
     * @return a new pick ray
     */
    public Ray getPickRay(float screenX, float screenY,
            float screenViewportX, float screenViewportY,
            float screenViewportWidth, float screenViewportHeight) {
        return getPickRay(screenX, screenY, screenViewportX, screenViewportY,
                screenViewportWidth, screenViewportHeight, new Ray());
    }

    /**
     * Writes a pick ray for a top-left-origin screen coordinate and screen viewport. Screen viewport values may use
     * logical pixels even when the camera viewport uses physical framebuffer pixels.
     *
     * @param screenX the screen x coordinate
     * @param screenY the screen y coordinate
     * @param screenViewportX the top-left screen viewport x coordinate
     * @param screenViewportY the top-left screen viewport y coordinate
     * @param screenViewportWidth the screen viewport width
     * @param screenViewportHeight the screen viewport height
     * @param out the output ray
     * @return the output ray
     */
    public Ray getPickRay(float screenX, float screenY,
            float screenViewportX, float screenViewportY,
            float screenViewportWidth, float screenViewportHeight, Ray out) {
        if (out == null) {
            throw new FdxException("Pick ray output cannot be null");
        }
        validateScreenViewport(screenViewportWidth, screenViewportHeight);
        float normalizedX = (screenX - screenViewportX) * 2.0f / screenViewportWidth - 1.0f;
        float normalizedY = 1.0f - (screenY - screenViewportY) * 2.0f / screenViewportHeight;
        update();
        unprojectUpdated(normalizedX, normalizedY, -1.0f, pickNear);
        unprojectUpdated(normalizedX, normalizedY, 1.0f, pickFar);
        return out.set(
                pickNear.x(), pickNear.y(), pickNear.z(),
                pickFar.x() - pickNear.x(),
                pickFar.y() - pickNear.y(),
                pickFar.z() - pickNear.z());
    }

    private static void validateProjectionArguments(Vector3 coordinates, Vector3 out,
            float screenViewportWidth, float screenViewportHeight) {
        if (coordinates == null) {
            throw new FdxException("Projection coordinates cannot be null");
        }
        if (out == null) {
            throw new FdxException("Projection output cannot be null");
        }
        validateScreenViewport(screenViewportWidth, screenViewportHeight);
    }

    private static void validateScreenViewport(float screenViewportWidth, float screenViewportHeight) {
        if (screenViewportWidth <= 0.0f || screenViewportHeight <= 0.0f) {
            throw new FdxException("Projection screen viewport dimensions must be greater than zero");
        }
    }
}
