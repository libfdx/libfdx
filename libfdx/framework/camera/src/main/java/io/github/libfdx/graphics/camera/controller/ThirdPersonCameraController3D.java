package io.github.libfdx.graphics.camera.controller;

import io.github.libfdx.core.FdxException;
import io.github.libfdx.graphics.camera.Camera;
import io.github.libfdx.input.Input;
import io.github.libfdx.math.Vector3;

/**
 * Follows a caller-owned 3D body or anchor from a configurable third-person view.
 *
 * @author xpenatan
 */
public class ThirdPersonCameraController3D extends CameraInputController3D {
    private static final float MIN_PITCH_RADIANS = (float)Math.toRadians(-85.0f);
    private static final float MAX_PITCH_RADIANS = (float)Math.toRadians(85.0f);
    private final Vector3 anchorPosition = new Vector3();
    private final Vector3 anchorUp = new Vector3(0.0f, 1.0f, 0.0f);
    private final float[] direction = new float[3];
    private final float[] right = new float[3];
    private final float[] up = new float[3];
    private CameraAnchor3D anchor;
    private float yawRadians;
    private float pitchRadians = (float)Math.toRadians(-12.0f);
    private float distance = 8.0f;
    private float minDistance = 1.0f;
    private float maxDistance = 64.0f;
    private float shoulderOffset;
    private float heightOffset = 1.6f;
    private float lookHeight = 1.2f;
    private float damping = 12.0f;
    private boolean initialized;
    private float cameraX;
    private float cameraY;
    private float cameraZ;
    private float lookX;
    private float lookY;
    private float lookZ;
    private float scrollDistanceFactor = 0.42f;

    public ThirdPersonCameraController3D(Input input, Camera camera, CameraAnchor3D anchor) {
        super(input, checkedCamera(camera));
        this.anchor = checkedAnchor(anchor);
    }

    public ThirdPersonCameraController3D anchor(CameraAnchor3D anchor) {
        this.anchor = checkedAnchor(anchor);
        initialized = false;
        return this;
    }

    public ThirdPersonCameraController3D distance(float distance) {
        this.distance = CameraMath.clamp(distance, minDistance, maxDistance);
        return this;
    }

    public ThirdPersonCameraController3D distanceRange(float minDistance, float maxDistance) {
        if (minDistance <= 0.0f || maxDistance < minDistance || Float.isNaN(minDistance)
                || Float.isNaN(maxDistance)) {
            throw new FdxException("ThirdPersonCameraController3D distance range is invalid");
        }
        this.minDistance = minDistance;
        this.maxDistance = maxDistance;
        distance = CameraMath.clamp(distance, minDistance, maxDistance);
        return this;
    }

    public ThirdPersonCameraController3D offsets(float shoulderOffset, float heightOffset, float lookHeight) {
        this.shoulderOffset = shoulderOffset;
        this.heightOffset = heightOffset;
        this.lookHeight = lookHeight;
        return this;
    }

    public ThirdPersonCameraController3D damping(float damping) {
        this.damping = Math.max(0.0f, damping);
        return this;
    }

    public ThirdPersonCameraController3D scrollDistanceFactor(float scrollDistanceFactor) {
        this.scrollDistanceFactor = Math.max(0.0f, scrollDistanceFactor);
        return this;
    }

    public ThirdPersonCameraController3D inputBindings(CameraInputBindings3D bindings) {
        bindings(bindings);
        return this;
    }

    public ThirdPersonCameraController3D pointerRegion(CameraPointerRegion pointerRegion) {
        super.pointerRegion(pointerRegion);
        return this;
    }

    public ThirdPersonCameraController3D activationListener(Runnable activationListener) {
        super.activationListener(activationListener);
        return this;
    }

    public ThirdPersonCameraController3D enabled(boolean enabled) {
        super.enabled(enabled);
        return this;
    }

    public ThirdPersonCameraController3D keyboardEnabled(boolean keyboardEnabled) {
        super.keyboardEnabled(keyboardEnabled);
        return this;
    }

    public ThirdPersonCameraController3D sensitivity(float sensitivityDegrees) {
        super.sensitivity(sensitivityDegrees);
        return this;
    }

    public ThirdPersonCameraController3D update(float deltaSeconds) {
        if (enabled()) {
            yawRadians += (float)Math.toRadians(consumeYawDegrees());
            pitchRadians = CameraMath.clamp(pitchRadians + (float)Math.toRadians(consumePitchDegrees()),
                    MIN_PITCH_RADIANS, MAX_PITCH_RADIANS);
            float scroll = consumeScrollY();
            if (scroll != 0.0f) {
                distance = CameraMath.clamp(distance + scroll * scrollDistanceFactor, minDistance, maxDistance);
            }
            updatePointerState();
        }
        return apply(deltaSeconds);
    }

    protected ThirdPersonCameraController3D apply(float deltaSeconds) {
        anchor.position(anchorPosition);
        anchor.up(anchorUp);
        CameraMath.directionFromAngles(yawRadians, pitchRadians, anchorUp.x(), anchorUp.y(), anchorUp.z(),
                direction, right, up);
        float targetLookX = anchorPosition.x() + up[0] * lookHeight;
        float targetLookY = anchorPosition.y() + up[1] * lookHeight;
        float targetLookZ = anchorPosition.z() + up[2] * lookHeight;
        float targetCameraX = anchorPosition.x() + up[0] * heightOffset + right[0] * shoulderOffset
                - direction[0] * distance;
        float targetCameraY = anchorPosition.y() + up[1] * heightOffset + right[1] * shoulderOffset
                - direction[1] * distance;
        float targetCameraZ = anchorPosition.z() + up[2] * heightOffset + right[2] * shoulderOffset
                - direction[2] * distance;
        float alpha = initialized ? CameraMath.damping(damping, deltaSeconds) : 1.0f;
        initialized = true;
        cameraX += (targetCameraX - cameraX) * alpha;
        cameraY += (targetCameraY - cameraY) * alpha;
        cameraZ += (targetCameraZ - cameraZ) * alpha;
        lookX += (targetLookX - lookX) * alpha;
        lookY += (targetLookY - lookY) * alpha;
        lookZ += (targetLookZ - lookZ) * alpha;
        camera.position(cameraX, cameraY, cameraZ)
                .lookAt(lookX, lookY, lookZ)
                .up(up[0], up[1], up[2])
                .update();
        return this;
    }

    private static Camera checkedCamera(Camera camera) {
        if (camera == null) {
            throw new FdxException("ThirdPersonCameraController3D camera cannot be null");
        }
        return camera;
    }

    private static CameraAnchor3D checkedAnchor(CameraAnchor3D anchor) {
        if (anchor == null) {
            throw new FdxException("ThirdPersonCameraController3D anchor cannot be null");
        }
        return anchor;
    }
}
