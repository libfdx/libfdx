package io.github.libfdx.graphics.camera;

import io.github.libfdx.core.FdxException;
import io.github.libfdx.math.ClipDepthRange;
import io.github.libfdx.math.Matrix4;
import io.github.libfdx.math.Ray;
import io.github.libfdx.math.Vector3;

/**
 * Represents a camera.
 *
 * @author xpenatan
 */
public class Camera {
    private final Vector3 position = new Vector3(0.0f, 0.0f, 1.0f);
    private final Vector3 direction = new Vector3(0.0f, 0.0f, -1.0f);
    private final Vector3 up = new Vector3(0.0f, 1.0f, 0.0f);
    private final Matrix4 projectionMatrix = new Matrix4();
    private final Matrix4 viewMatrix = new Matrix4();
    private final Matrix4 combinedMatrix = new Matrix4();
    private final Matrix4 inverseProjectionMatrix = new Matrix4();
    private final Matrix4 inverseViewMatrix = new Matrix4();
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
     * Null means "follow the active default", resolved on read rather than at
     * construction: an editor switches to reversed depth when a project loads,
     * long after its cameras exist, and a camera that captured the old value
     * would keep building a projection the depth test no longer agrees with.
     */
    private ClipDepthRange clipDepthRange;

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
        // near == 0 is legitimate for an orthographic projection; only a
        // perspective divide needs a strictly positive near plane, and that is
        // clamped in update().
        //
        // far <= 0 means "no far plane". It is the opt-in for an infinite
        // projection, which needs reversed depth to be representable.
        if (near < 0.0f || (far > 0.0f && far <= near)) {
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
        return this;
    }

    /**
     * Sets the look at and returns this camera.
     *
     * <p>The direction is derived as {@code target - position}. When both are
     * far from the origin and close to each other the subtraction loses
     * precision proportionally, so prefer
     * {@link #direction(float, float, float)} with an already-relative vector
     * in a large world.</p>
     *
     * @param x the x coordinate
     * @param y the y coordinate
     * @param z the z coordinate
     * @return this camera for chaining
     */
    public Camera lookAt(float x, float y, float z) {
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
            float effectiveNear = Math.max(near, 0.0001f);
            if (hasInfiniteFarPlane()) {
                projectionMatrix.setToPerspectiveInfinite(fieldOfViewDegrees,
                        viewportWidth / viewportHeight, effectiveNear, clipDepthRange());
            }
            else {
                // far <= 0 asked for no far plane, but this range cannot express
                // one - the OpenGL family, most likely. Degrade to the widest
                // finite range that still extracts sane frustum planes: a ratio
                // of 1e6 stays an order of magnitude clear of the 2^24 point
                // where the far plane row stops normalizing. The view distance
                // is limited rather than the projection being invalid.
                float effectiveFar = far > 0.0f ? far : effectiveNear * 1.0e6f;
                projectionMatrix.setToPerspective(fieldOfViewDegrees, viewportWidth / viewportHeight,
                        effectiveNear, effectiveFar, clipDepthRange());
            }
        }
        else {
            float width = viewportWidth * zoom;
            float height = viewportHeight * zoom;
            projectionMatrix.setToOrthographic(-width * 0.5f, width * 0.5f, -height * 0.5f, height * 0.5f, near, far,
                    clipDepthRange());
        }
        // Keep the inverse path in float without taking a determinant that mixes projection with far-world translation.
        inverseProjectionMatrix.set(projectionMatrix).invert();
        // Build the basis from the unit direction alone, then apply the eye.
        //
        // Deriving a look-at target as position + direction adds a unit vector
        // to the eye coordinate. Once |position| passes about 1.7e7 the add
        // rounds back to position, setToLookAt recovers a zero forward vector
        // and silently substitutes forward = (0,0,-1), so the camera loses its
        // orientation with no error reported. That is fatal for large worlds,
        // where an eye at 1e11 has a float ULP of 16 km.
        viewMatrix.setToLookAlong(
                direction.x(), direction.y(), direction.z(),
                up.x(), up.y(), up.z());
        viewMatrix.translate(-position.x(), -position.y(), -position.z());
        inverseViewMatrix.set(viewMatrix).invert();
        combinedMatrix.setToMul(projectionMatrix, viewMatrix);
        return this;
    }

    /**
     * Returns the inverse projection matrix.
     *
     * <p>Exposed so callers can build an inverse projection-view as
     * {@code inverseView * inverseProjection} rather than inverting the
     * product. Inverting the product is not safe at large camera distances:
     * once the projection's near term falls below one ULP of the view
     * translation it is lost in the multiply, rows 2 and 3 of the product
     * become parallel and the determinant collapses to zero. Each factor
     * inverts stably on its own.</p>
     *
     * @return the inverse projection matrix
     */
    public Matrix4 inverseProjectionMatrix() {
        update();
        return inverseProjectionMatrix;
    }

    /**
     * Returns the inverse view matrix. See {@link #inverseProjectionMatrix()}
     * for why the two inverses are kept apart.
     *
     * @return the inverse view matrix
     */
    public Matrix4 inverseViewMatrix() {
        update();
        return inverseViewMatrix;
    }

    /**
     * Returns this camera's clip depth range.
     *
     * @return the clip depth range
     */
    public ClipDepthRange clipDepthRange() {
        return clipDepthRange != null ? clipDepthRange : ClipDepthRange.getDefault();
    }

    /**
     * Sets this camera's clip depth range, overriding the static default.
     *
     * @param clipDepthRange the range the target API clips against
     * @return this camera for chaining
     */
    public Camera clipDepthRange(ClipDepthRange clipDepthRange) {
        if (clipDepthRange == null) {
            throw new FdxException("Clip depth range cannot be null");
        }
        if (clipDepthRange == ClipDepthRange.ZERO_TO_ONE_REVERSED
                && !ClipDepthRange.getDefault().isZeroToOne()) {
            // The OpenGL family cannot clip depth to 0..w at all, so reversed
            // depth would render wrong with nothing to indicate it. Fail here
            // instead.
            throw new FdxException(
                    "Reversed depth requires a zero-to-one graphics API; the active device uses "
                            + ClipDepthRange.getDefault());
        }
        this.clipDepthRange = clipDepthRange;
        return this;
    }

    /**
     * Returns whether this camera has no far plane, i.e. nothing is clipped by
     * distance. True when {@code far <= 0}, which requires reversed depth.
     *
     * @return true when the far plane is at infinity
     */
    public boolean hasInfiniteFarPlane() {
        return far <= 0.0f && clipDepthRange().isReversed();
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
                clipDepthRange().isZeroToOne()
                        ? out.z() : (out.z() + 1.0f) * 0.5f);
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
        // Window depth is always 0..1; only the clip-space mapping differs.
        float normalizedZ = clipDepthRange().isZeroToOne()
                ? screenCoordinates.z() : screenCoordinates.z() * 2.0f - 1.0f;
        update();
        return unprojectUpdated(normalizedX, normalizedY, normalizedZ, out);
    }

    private Vector3 unprojectUpdated(float normalizedX, float normalizedY, float normalizedZ, Vector3 out) {
        out.set(normalizedX, normalizedY, normalizedZ);
        inverseProjectionMatrix.transformProjective(out, out);
        return inverseViewMatrix.transformPosition(out, out);
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
        float nearDepth = clipDepthRange().nearPlaneDepth();
        unprojectUpdated(normalizedX, normalizedY, nearDepth, pickNear);
        // The direction is NOT taken from a far-plane point. With an infinite
        // far plane that point is at infinity, and even with a finite one the
        // unprojection divides by a w that is a difference of two nearly equal
        // terms, so a large far/near ratio leaves it with almost no precision.
        //
        // In view space the eye is the origin, so the near-plane point IS the
        // direction. Rotating it into world space never touches the eye
        // translation, making the result independent of far and of how far the
        // camera has travelled.
        if (projection == CameraProjection.PERSPECTIVE) {
            pickFar.set(normalizedX, normalizedY, nearDepth);
            inverseProjectionMatrix.transformProjective(pickFar, pickFar);
        }
        else {
            // Orthographic rays are parallel: the screen position sets the
            // origin, and every direction is the camera forward.
            pickFar.set(0.0f, 0.0f, -1.0f);
        }
        inverseViewMatrix.transformDirection(pickFar, pickFar);
        return out.set(
                pickNear.x(), pickNear.y(), pickNear.z(),
                pickFar.x(), pickFar.y(), pickFar.z());
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
