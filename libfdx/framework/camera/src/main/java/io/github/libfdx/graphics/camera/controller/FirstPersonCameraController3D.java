package io.github.libfdx.graphics.camera.controller;

import io.github.libfdx.core.FdxException;
import io.github.libfdx.graphics.camera.Camera;
import io.github.libfdx.input.Input;
import io.github.libfdx.math.Vector3;

/**
 * Rotates a camera attached to a caller-owned 3D body or anchor.
 *
 * @author xpenatan
 */
public class FirstPersonCameraController3D extends CameraInputController3D {
    private static final float MIN_PITCH_RADIANS = (float)Math.toRadians(-89.0f);
    private static final float MAX_PITCH_RADIANS = (float)Math.toRadians(89.0f);
    private final Vector3 anchorPosition = new Vector3();
    private final Vector3 anchorUp = new Vector3(0.0f, 1.0f, 0.0f);
    private final float[] direction = new float[3];
    private final float[] right = new float[3];
    private final float[] up = new float[3];
    private CameraAnchor3D anchor;
    private float yawRadians;
    private float pitchRadians;
    private float eyeRightOffset;
    private float eyeUpOffset;
    private float eyeForwardOffset;

    public FirstPersonCameraController3D(Input input, Camera camera, CameraAnchor3D anchor) {
        super(input, checkedCamera(camera));
        this.anchor = checkedAnchor(anchor);
        syncAnglesFromCamera();
    }

    public FirstPersonCameraController3D anchor(CameraAnchor3D anchor) {
        this.anchor = checkedAnchor(anchor);
        return this;
    }

    public FirstPersonCameraController3D eyeOffset(float right, float up, float forward) {
        eyeRightOffset = right;
        eyeUpOffset = up;
        eyeForwardOffset = forward;
        return this;
    }

    public FirstPersonCameraController3D inputBindings(CameraInputBindings3D bindings) {
        bindings(bindings);
        return this;
    }

    public FirstPersonCameraController3D pointerRegion(CameraPointerRegion pointerRegion) {
        super.pointerRegion(pointerRegion);
        return this;
    }

    public FirstPersonCameraController3D activationListener(Runnable activationListener) {
        super.activationListener(activationListener);
        return this;
    }

    public FirstPersonCameraController3D enabled(boolean enabled) {
        super.enabled(enabled);
        return this;
    }

    public FirstPersonCameraController3D touchEnabled(boolean touchEnabled) {
        super.touchEnabled(touchEnabled);
        return this;
    }

    public FirstPersonCameraController3D keyboardEnabled(boolean keyboardEnabled) {
        super.keyboardEnabled(keyboardEnabled);
        return this;
    }

    public FirstPersonCameraController3D sensitivity(float sensitivityDegrees) {
        super.sensitivity(sensitivityDegrees);
        return this;
    }

    public FirstPersonCameraController3D invert(boolean invertX, boolean invertY) {
        super.invert(invertX, invertY);
        return this;
    }

    public FirstPersonCameraController3D update(float deltaSeconds) {
        if (enabled()) {
            yawRadians += (float)Math.toRadians(consumeYawDegrees());
            pitchRadians = CameraMath.clamp(pitchRadians + (float)Math.toRadians(consumePitchDegrees()),
                    MIN_PITCH_RADIANS, MAX_PITCH_RADIANS);
            consumeScrollY();
            updatePointerState();
        }
        return apply(deltaSeconds);
    }

    protected FirstPersonCameraController3D apply(float deltaSeconds) {
        anchor.position(anchorPosition);
        anchor.up(anchorUp);
        CameraMath.directionFromAngles(yawRadians, pitchRadians, anchorUp.x(), anchorUp.y(), anchorUp.z(),
                direction, right, up);
        float x = anchorPosition.x() + right[0] * eyeRightOffset + up[0] * eyeUpOffset
                + direction[0] * eyeForwardOffset;
        float y = anchorPosition.y() + right[1] * eyeRightOffset + up[1] * eyeUpOffset
                + direction[1] * eyeForwardOffset;
        float z = anchorPosition.z() + right[2] * eyeRightOffset + up[2] * eyeUpOffset
                + direction[2] * eyeForwardOffset;
        camera.position(x, y, z)
                .direction(direction[0], direction[1], direction[2])
                .up(up[0], up[1], up[2])
                .update();
        return this;
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
            throw new FdxException("FirstPersonCameraController3D camera cannot be null");
        }
        return camera;
    }

    private static CameraAnchor3D checkedAnchor(CameraAnchor3D anchor) {
        if (anchor == null) {
            throw new FdxException("FirstPersonCameraController3D anchor cannot be null");
        }
        return anchor;
    }
}
