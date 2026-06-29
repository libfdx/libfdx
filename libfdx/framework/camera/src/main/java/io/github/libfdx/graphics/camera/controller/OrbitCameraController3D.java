package io.github.libfdx.graphics.camera.controller;

import io.github.libfdx.core.FdxException;
import io.github.libfdx.graphics.camera.Camera;
import io.github.libfdx.input.Input;

/**
 * Orbits a camera around a target point.
 *
 * @author xpenatan
 */
public class OrbitCameraController3D extends CameraInputController3D {
    private static final float MIN_PITCH_RADIANS = (float)Math.toRadians(-85.0f);
    private static final float MAX_PITCH_RADIANS = (float)Math.toRadians(85.0f);
    private final float[] direction = new float[3];
    private final float[] right = new float[3];
    private final float[] up = new float[] { 0.0f, 1.0f, 0.0f };
    private float targetX;
    private float targetY;
    private float targetZ;
    private float yawRadians;
    private float pitchRadians = (float)Math.toRadians(-20.0f);
    private float radius = 8.0f;
    private float minRadius = 1.0f;
    private float maxRadius = 64.0f;
    private float autoYawDegreesPerFrame;
    private boolean autoOrbitEnabled;
    private boolean autoOrbitFinite;
    private long autoOrbitFrames;
    private long autoOrbitFrame;
    private float autoOrbitTotalDegrees;

    public OrbitCameraController3D(Input input, Camera camera) {
        super(input, checkedCamera(camera));
        syncFromCamera();
    }

    public OrbitCameraController3D target(float x, float y, float z) {
        targetX = x;
        targetY = y;
        targetZ = z;
        return apply();
    }

    public OrbitCameraController3D position(float cameraX, float cameraY, float cameraZ,
            float targetX, float targetY, float targetZ) {
        this.targetX = targetX;
        this.targetY = targetY;
        this.targetZ = targetZ;
        float x = cameraX - targetX;
        float y = cameraY - targetY;
        float z = cameraZ - targetZ;
        radius = (float)Math.sqrt(x * x + y * y + z * z);
        if (radius <= 0.0f) {
            radius = 1.0f;
        }
        yawRadians = (float)Math.atan2(x, z);
        pitchRadians = (float)Math.asin(CameraMath.clamp(y / radius, -1.0f, 1.0f));
        return apply();
    }

    public OrbitCameraController3D radiusRange(float minRadius, float maxRadius) {
        if (minRadius <= 0.0f || maxRadius < minRadius || Float.isNaN(minRadius) || Float.isNaN(maxRadius)) {
            throw new FdxException("OrbitCameraController3D radius range is invalid");
        }
        this.minRadius = minRadius;
        this.maxRadius = maxRadius;
        radius = CameraMath.clamp(radius, minRadius, maxRadius);
        return apply();
    }

    public OrbitCameraController3D radius(float radius) {
        this.radius = CameraMath.clamp(radius, minRadius, maxRadius);
        return apply();
    }

    public OrbitCameraController3D inputBindings(CameraInputBindings3D bindings) {
        bindings(bindings);
        return this;
    }

    public OrbitCameraController3D pointerRegion(CameraPointerRegion pointerRegion) {
        super.pointerRegion(pointerRegion);
        return this;
    }

    public OrbitCameraController3D activationListener(Runnable activationListener) {
        super.activationListener(activationListener);
        return this;
    }

    public OrbitCameraController3D enabled(boolean enabled) {
        super.enabled(enabled);
        return this;
    }

    public OrbitCameraController3D sensitivity(float sensitivityDegrees) {
        super.sensitivity(sensitivityDegrees);
        return this;
    }

    public OrbitCameraController3D keyboardEnabled(boolean keyboardEnabled) {
        super.keyboardEnabled(keyboardEnabled);
        return this;
    }

    public OrbitCameraController3D autoOrbit(boolean enabled, float yawDegreesPerFrame, long frames,
            float startDegrees, float totalDegrees) {
        autoOrbitEnabled = enabled;
        autoYawDegreesPerFrame = yawDegreesPerFrame;
        autoOrbitFrames = Math.max(0L, frames);
        autoOrbitFinite = autoOrbitFrames > 1L;
        autoOrbitTotalDegrees = totalDegrees;
        autoOrbitFrame = 0L;
        if (enabled && startDegrees != 0.0f) {
            yawRadians += (float)Math.toRadians(startDegrees);
        }
        return this;
    }

    public OrbitCameraController3D update(float deltaSeconds) {
        boolean manual = false;
        if (enabled()) {
            float yaw = consumeYawDegrees();
            float pitch = consumePitchDegrees();
            float scroll = consumeScrollY();
            manual = yaw != 0.0f || pitch != 0.0f || scroll != 0.0f;
            yawRadians += (float)Math.toRadians(yaw);
            pitchRadians = CameraMath.clamp(pitchRadians + (float)Math.toRadians(pitch),
                    MIN_PITCH_RADIANS, MAX_PITCH_RADIANS);
            radius = CameraMath.clamp(radius + scroll * 0.42f, minRadius, maxRadius);
            if (input != null) {
                float speed = 3.4f * Math.max(0.0f, deltaSeconds) * Math.max(1.0f, radius * 0.22f);
                if (key(bindings().leftKey(), bindings().alternateLeftKey())) {
                    pan(-speed, 0.0f);
                    manual = true;
                }
                if (key(bindings().rightKey(), bindings().alternateRightKey())) {
                    pan(speed, 0.0f);
                    manual = true;
                }
                if (key(bindings().upKey(), bindings().alternateUpKey())) {
                    targetY += speed;
                    manual = true;
                }
                if (key(bindings().downKey(), bindings().alternateDownKey())) {
                    targetY -= speed;
                    manual = true;
                }
            }
            updatePointerState();
        }
        if (autoOrbitEnabled && !manual) {
            applyAutoOrbit();
        }
        return apply();
    }

    protected OrbitCameraController3D apply() {
        CameraMath.directionFromAngles(yawRadians, pitchRadians, up[0], up[1], up[2], direction, right, up);
        camera.position(targetX - direction[0] * radius, targetY - direction[1] * radius,
                        targetZ - direction[2] * radius)
                .direction(direction[0], direction[1], direction[2])
                .up(up[0], up[1], up[2])
                .update();
        return this;
    }

    private void pan(float rightDelta, float forwardDelta) {
        targetX += right[0] * rightDelta + direction[0] * forwardDelta;
        targetZ += right[2] * rightDelta + direction[2] * forwardDelta;
    }

    private void applyAutoOrbit() {
        if (autoOrbitFinite) {
            float progress = Math.min(autoOrbitFrame, autoOrbitFrames - 1L) / (float)(autoOrbitFrames - 1L);
            float previousProgress = autoOrbitFrame > 0L
                    ? (autoOrbitFrame - 1L) / (float)(autoOrbitFrames - 1L)
                    : 0.0f;
            yawRadians += (float)Math.toRadians(autoOrbitTotalDegrees * (progress - previousProgress));
            autoOrbitFrame++;
            return;
        }
        yawRadians += (float)Math.toRadians(autoYawDegreesPerFrame);
        autoOrbitFrame++;
    }

    private void syncFromCamera() {
        float dx = camera.direction().x();
        float dy = camera.direction().y();
        float dz = camera.direction().z();
        yawRadians = (float)Math.atan2(-dx, -dz);
        pitchRadians = (float)Math.asin(CameraMath.clamp(-dy, -1.0f, 1.0f));
        targetX = camera.position().x() + dx * radius;
        targetY = camera.position().y() + dy * radius;
        targetZ = camera.position().z() + dz * radius;
    }

    private static Camera checkedCamera(Camera camera) {
        if (camera == null) {
            throw new FdxException("OrbitCameraController3D camera cannot be null");
        }
        return camera;
    }
}
