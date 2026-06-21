package io.github.libfdx.graphics.camera.controller;

import io.github.libfdx.core.FdxException;
import io.github.libfdx.graphics.camera.Camera;
import io.github.libfdx.graphics.camera.CameraProjection;
import io.github.libfdx.input.Input;

/**
 * Provides orthographic 3D editor/debug pan and zoom controls.
 *
 * @author xpenatan
 */
public class OrthographicCameraController3D extends CameraInputController3D {
    private float positionX;
    private float positionY;
    private float positionZ;
    private float zoomSpeed = 0.14f;
    private float minZoom = 0.0035f;
    private float maxZoom = 0.04f;

    public OrthographicCameraController3D(Input input, Camera camera) {
        super(input, checkedCamera(camera));
        camera.projection(CameraProjection.ORTHOGRAPHIC).direction(0.0f, -0.35f, -1.0f).up(0.0f, 1.0f, 0.0f);
        positionX = camera.position().x();
        positionY = camera.position().y();
        positionZ = camera.position().z();
    }

    public OrthographicCameraController3D position(float x, float y, float z) {
        positionX = x;
        positionY = y;
        positionZ = z;
        return apply();
    }

    public OrthographicCameraController3D zoomRange(float minZoom, float maxZoom) {
        if (minZoom <= 0.0f || maxZoom < minZoom || Float.isNaN(minZoom) || Float.isNaN(maxZoom)) {
            throw new FdxException("OrthographicCameraController3D zoom range is invalid");
        }
        this.minZoom = minZoom;
        this.maxZoom = maxZoom;
        camera.zoom(CameraMath.clamp(camera.zoom(), minZoom, maxZoom));
        return apply();
    }

    public OrthographicCameraController3D zoomSpeed(float zoomSpeed) {
        this.zoomSpeed = Math.max(0.0f, zoomSpeed);
        return this;
    }

    public OrthographicCameraController3D inputBindings(CameraInputBindings3D bindings) {
        bindings(bindings);
        return this;
    }

    public OrthographicCameraController3D pointerRegion(CameraPointerRegion pointerRegion) {
        super.pointerRegion(pointerRegion);
        return this;
    }

    public OrthographicCameraController3D activationListener(Runnable activationListener) {
        super.activationListener(activationListener);
        return this;
    }

    public OrthographicCameraController3D enabled(boolean enabled) {
        super.enabled(enabled);
        return this;
    }

    public OrthographicCameraController3D keyboardEnabled(boolean keyboardEnabled) {
        super.keyboardEnabled(keyboardEnabled);
        return this;
    }

    public OrthographicCameraController3D update(float deltaSeconds) {
        if (enabled()) {
            float yaw = consumeYawDegrees();
            float pitch = consumePitchDegrees();
            float scroll = consumeScrollY();
            positionX += yaw * camera.zoom() * 120.0f;
            positionY += pitch * camera.zoom() * 120.0f;
            if (scroll != 0.0f) {
                camera.zoom(CameraMath.clamp(camera.zoom() * (1.0f + scroll * zoomSpeed), minZoom, maxZoom));
            }
            if (input != null) {
                float pan = 480.0f * camera.zoom() * Math.max(0.0f, deltaSeconds);
                if (key(bindings().leftKey(), bindings().alternateLeftKey())) {
                    positionX -= pan;
                }
                if (key(bindings().rightKey(), bindings().alternateRightKey())) {
                    positionX += pan;
                }
                if (key(bindings().forwardKey(), bindings().alternateForwardKey())) {
                    positionZ -= pan;
                }
                if (key(bindings().backwardKey(), bindings().alternateBackwardKey())) {
                    positionZ += pan;
                }
            }
            updatePointerState();
        }
        return apply();
    }

    protected OrthographicCameraController3D apply() {
        camera.projection(CameraProjection.ORTHOGRAPHIC)
                .position(positionX, positionY, positionZ)
                .direction(0.0f, -0.35f, -1.0f)
                .up(0.0f, 1.0f, 0.0f)
                .update();
        return this;
    }

    private static Camera checkedCamera(Camera camera) {
        if (camera == null) {
            throw new FdxException("OrthographicCameraController3D camera cannot be null");
        }
        return camera;
    }
}
