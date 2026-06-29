package io.github.libfdx.graphics.camera.controller;

import io.github.libfdx.core.FdxException;
import io.github.libfdx.graphics.camera.Camera;
import io.github.libfdx.input.Input;

/**
 * Moves a camera directly with free/editor style 3D controls.
 *
 * @author xpenatan
 */
public class FreeCameraController3D extends CameraInputController3D {
    private static final float MIN_PITCH_RADIANS = (float)Math.toRadians(-89.0f);
    private static final float MAX_PITCH_RADIANS = (float)Math.toRadians(89.0f);
    private final float[] direction = new float[3];
    private final float[] right = new float[3];
    private final float[] up = new float[] { 0.0f, 1.0f, 0.0f };
    private float positionX;
    private float positionY;
    private float positionZ;
    private float yawRadians;
    private float pitchRadians;
    private float moveSpeed = 3.4f;
    private float minMoveSpeed = 0.001f;
    private float maxMoveSpeed = Float.POSITIVE_INFINITY;
    private float scrollSpeedFactor = 0.10f;
    private float fastMultiplier = 2.35f;
    private float boostMultiplier = 2.0f;

    public FreeCameraController3D(Input input, Camera camera) {
        super(input, checkedCamera(camera));
        positionX = camera.position().x();
        positionY = camera.position().y();
        positionZ = camera.position().z();
        syncAnglesFromCamera();
    }

    public FreeCameraController3D position(float x, float y, float z) {
        positionX = x;
        positionY = y;
        positionZ = z;
        return apply();
    }

    public FreeCameraController3D up(float x, float y, float z) {
        up[0] = x;
        up[1] = y;
        up[2] = z;
        CameraMath.normalize(up);
        return apply();
    }

    public FreeCameraController3D speed(float speed) {
        if (speed < 0.0f || Float.isNaN(speed)) {
            throw new FdxException("Free camera speed cannot be negative");
        }
        moveSpeed = CameraMath.clamp(speed, minMoveSpeed, maxMoveSpeed);
        return this;
    }

    public float speed() {
        return moveSpeed;
    }

    public FreeCameraController3D speedRange(float minSpeed, float maxSpeed) {
        if (minSpeed < 0.0f || maxSpeed < minSpeed || Float.isNaN(minSpeed) || Float.isNaN(maxSpeed)) {
            throw new FdxException("Free camera speed range is invalid");
        }
        minMoveSpeed = minSpeed;
        maxMoveSpeed = maxSpeed;
        moveSpeed = CameraMath.clamp(moveSpeed, minMoveSpeed, maxMoveSpeed);
        return this;
    }

    public FreeCameraController3D scrollSpeedFactor(float scrollSpeedFactor) {
        this.scrollSpeedFactor = Math.max(0.0f, scrollSpeedFactor);
        return this;
    }

    public FreeCameraController3D speedMultipliers(float fastMultiplier, float boostMultiplier) {
        this.fastMultiplier = Math.max(0.0f, fastMultiplier);
        this.boostMultiplier = Math.max(0.0f, boostMultiplier);
        return this;
    }

    public FreeCameraController3D inputBindings(CameraInputBindings3D bindings) {
        bindings(bindings);
        return this;
    }

    public FreeCameraController3D pointerRegion(CameraPointerRegion pointerRegion) {
        super.pointerRegion(pointerRegion);
        return this;
    }

    public FreeCameraController3D activationListener(Runnable activationListener) {
        super.activationListener(activationListener);
        return this;
    }

    public FreeCameraController3D enabled(boolean enabled) {
        super.enabled(enabled);
        return this;
    }

    public FreeCameraController3D touchEnabled(boolean touchEnabled) {
        super.touchEnabled(touchEnabled);
        return this;
    }

    public FreeCameraController3D keyboardEnabled(boolean keyboardEnabled) {
        super.keyboardEnabled(keyboardEnabled);
        return this;
    }

    public FreeCameraController3D sensitivity(float sensitivityDegrees) {
        super.sensitivity(sensitivityDegrees);
        return this;
    }

    public FreeCameraController3D invert(boolean invertX, boolean invertY) {
        super.invert(invertX, invertY);
        return this;
    }

    public FreeCameraController3D update(float deltaSeconds) {
        if (!enabled()) {
            return apply();
        }
        yawRadians += (float)Math.toRadians(consumeYawDegrees());
        pitchRadians = CameraMath.clamp(pitchRadians + (float)Math.toRadians(consumePitchDegrees()),
                MIN_PITCH_RADIANS, MAX_PITCH_RADIANS);
        float scroll = consumeScrollY();
        if (scroll != 0.0f) {
            moveSpeed = CameraMath.clamp(moveSpeed * (1.0f - scroll * scrollSpeedFactor),
                    minMoveSpeed, maxMoveSpeed);
        }
        CameraMath.directionFromAngles(yawRadians, pitchRadians, up[0], up[1], up[2], direction, right, up);
        if (input != null) {
            float speed = moveSpeed * Math.max(0.0f, deltaSeconds);
            if (fastActive()) {
                speed *= fastMultiplier;
            }
            if (boostActive()) {
                speed *= boostMultiplier;
            }
            if (key(bindings().forwardKey(), bindings().alternateForwardKey())) {
                move(direction[0] * speed, direction[1] * speed, direction[2] * speed);
            }
            if (key(bindings().backwardKey(), bindings().alternateBackwardKey())) {
                move(-direction[0] * speed, -direction[1] * speed, -direction[2] * speed);
            }
            if (key(bindings().rightKey(), bindings().alternateRightKey())) {
                move(right[0] * speed, right[1] * speed, right[2] * speed);
            }
            if (key(bindings().leftKey(), bindings().alternateLeftKey())) {
                move(-right[0] * speed, -right[1] * speed, -right[2] * speed);
            }
            if (key(bindings().upKey(), bindings().alternateUpKey())) {
                move(up[0] * speed, up[1] * speed, up[2] * speed);
            }
            if (key(bindings().downKey(), bindings().alternateDownKey())) {
                move(-up[0] * speed, -up[1] * speed, -up[2] * speed);
            }
        }
        updatePointerState();
        return apply();
    }

    protected FreeCameraController3D apply() {
        CameraMath.directionFromAngles(yawRadians, pitchRadians, up[0], up[1], up[2], direction, right, up);
        camera.position(positionX, positionY, positionZ)
                .direction(direction[0], direction[1], direction[2])
                .up(up[0], up[1], up[2])
                .update();
        return this;
    }

    private void move(float x, float y, float z) {
        positionX += x;
        positionY += y;
        positionZ += z;
    }

    private void syncAnglesFromCamera() {
        float dx = camera.direction().x();
        float dy = camera.direction().y();
        float dz = camera.direction().z();
        yawRadians = (float)Math.atan2(-dx, -dz);
        pitchRadians = (float)Math.asin(CameraMath.clamp(-dy, -1.0f, 1.0f));
    }

    private static Camera checkedCamera(Camera camera) {
        if (camera == null) {
            throw new FdxException("FreeCameraController3D camera cannot be null");
        }
        return camera;
    }
}
