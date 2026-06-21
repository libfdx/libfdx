package io.github.libfdx.graphics.camera.controller;

import io.github.libfdx.core.FdxException;
import io.github.libfdx.graphics.camera.Camera;
import io.github.libfdx.graphics.camera.CameraProjection;
import io.github.libfdx.math.Vector2;
import io.github.libfdx.math.Vector3;

/**
 * Smooth projection-aware camera controller for scene intros, trailers, and flybys.
 *
 * @author xpenatan
 */
public class CinematicCameraController {
    private final Camera camera;
    private final Vector2 anchor2DPosition = new Vector2();
    private final Vector3 anchor3DPosition = new Vector3();
    private final Vector3 anchor3DUp = new Vector3(0.0f, 1.0f, 0.0f);
    private final CinematicCameraPathSample3D pathSample3D = new CinematicCameraPathSample3D();
    private final float[] direction = new float[3];
    private final float[] right = new float[3];
    private final float[] up = new float[3];
    private CameraAnchor2D anchor2D;
    private CameraAnchor3D anchor3D;
    private CinematicCameraPath3D path3D;
    private boolean mode3D;
    private boolean initialized;
    private float damping = 8.0f;
    private float pathTimeSeconds;
    private float pathPlaybackSpeed = 1.0f;
    private float cameraX;
    private float cameraY;
    private float cameraZ;
    private float lookX;
    private float lookY;
    private float lookZ;
    private float offsetX;
    private float offsetY;
    private float zoom = 1.0f;
    private float rotationRadians;
    private float yawRadians;
    private float pitchRadians = (float)Math.toRadians(-12.0f);
    private float distance = 8.0f;
    private float shoulderOffset;
    private float heightOffset = 1.6f;
    private float lookHeight = 1.2f;

    public CinematicCameraController(Camera camera) {
        if (camera == null) {
            throw new FdxException("CinematicCameraController camera cannot be null");
        }
        this.camera = camera;
    }

    public CinematicCameraController anchor(CameraAnchor2D anchor) {
        anchor2D = anchor;
        path3D = null;
        mode3D = false;
        initialized = false;
        return this;
    }

    public CinematicCameraController anchor(CameraAnchor3D anchor) {
        anchor3D = anchor;
        path3D = null;
        mode3D = true;
        initialized = false;
        return this;
    }

    public CinematicCameraController path3D(CinematicCameraPath3D path) {
        path3D = path;
        if (path != null) {
            mode3D = true;
        }
        initialized = false;
        return this;
    }

    public CinematicCameraController pathTime(float timeSeconds) {
        if (!Float.isFinite(timeSeconds)) {
            throw new FdxException("CinematicCameraController path time must be finite");
        }
        pathTimeSeconds = timeSeconds;
        return this;
    }

    public CinematicCameraController pathPlaybackSpeed(float secondsPerSecond) {
        if (!Float.isFinite(secondsPerSecond)) {
            throw new FdxException("CinematicCameraController path playback speed must be finite");
        }
        pathPlaybackSpeed = secondsPerSecond;
        return this;
    }

    public CinematicCameraController damping(float damping) {
        this.damping = Math.max(0.0f, damping);
        return this;
    }

    public CinematicCameraController offset2D(float x, float y) {
        offsetX = x;
        offsetY = y;
        return this;
    }

    public CinematicCameraController zoom(float zoom) {
        if (zoom <= 0.0f || Float.isNaN(zoom)) {
            throw new FdxException("CinematicCameraController zoom must be greater than zero");
        }
        this.zoom = zoom;
        return this;
    }

    public CinematicCameraController rotation(float radians) {
        rotationRadians = radians;
        return this;
    }

    public CinematicCameraController orbit(float yawDegrees, float pitchDegrees, float distance) {
        yawRadians = (float)Math.toRadians(yawDegrees);
        pitchRadians = (float)Math.toRadians(pitchDegrees);
        this.distance = Math.max(0.001f, distance);
        return this;
    }

    public CinematicCameraController rotate(float yawDegrees, float pitchDegrees) {
        yawRadians += (float)Math.toRadians(yawDegrees);
        pitchRadians += (float)Math.toRadians(pitchDegrees);
        return this;
    }

    public CinematicCameraController offsets3D(float shoulderOffset, float heightOffset, float lookHeight) {
        this.shoulderOffset = shoulderOffset;
        this.heightOffset = heightOffset;
        this.lookHeight = lookHeight;
        return this;
    }

    public CinematicCameraController update(float deltaSeconds) {
        if (mode3D) {
            update3D(deltaSeconds);
        }
        else {
            update2D(deltaSeconds);
        }
        return this;
    }

    protected void update2D(float deltaSeconds) {
        if (anchor2D == null) {
            camera.update();
            return;
        }
        anchor2D.position(anchor2DPosition);
        float targetX = anchor2DPosition.x() + offsetX;
        float targetY = anchor2DPosition.y() + offsetY;
        float alpha = initialized ? CameraMath.damping(damping, deltaSeconds) : 1.0f;
        initialized = true;
        cameraX += (targetX - cameraX) * alpha;
        cameraY += (targetY - cameraY) * alpha;
        camera.projection(CameraProjection.ORTHOGRAPHIC)
                .position(cameraX, cameraY, camera.position().z())
                .direction(0.0f, 0.0f, -1.0f)
                .up(-(float)Math.sin(rotationRadians), (float)Math.cos(rotationRadians), 0.0f)
                .zoom(zoom)
                .update();
    }

    protected void update3D(float deltaSeconds) {
        if (path3D != null) {
            pathTimeSeconds += Math.max(0.0f, deltaSeconds) * pathPlaybackSpeed;
            path3D.sample(pathTimeSeconds, pathSample3D);
            float alpha = initialized ? CameraMath.damping(damping, deltaSeconds) : 1.0f;
            initialized = true;
            cameraX += (pathSample3D.cameraX() - cameraX) * alpha;
            cameraY += (pathSample3D.cameraY() - cameraY) * alpha;
            cameraZ += (pathSample3D.cameraZ() - cameraZ) * alpha;
            lookX += (pathSample3D.lookAtX() - lookX) * alpha;
            lookY += (pathSample3D.lookAtY() - lookY) * alpha;
            lookZ += (pathSample3D.lookAtZ() - lookZ) * alpha;
            camera.position(cameraX, cameraY, cameraZ)
                    .lookAt(lookX, lookY, lookZ)
                    .up(pathSample3D.upX(), pathSample3D.upY(), pathSample3D.upZ())
                    .update();
            return;
        }
        if (anchor3D == null) {
            camera.update();
            return;
        }
        anchor3D.position(anchor3DPosition);
        anchor3D.up(anchor3DUp);
        CameraMath.directionFromAngles(yawRadians, pitchRadians, anchor3DUp.x(), anchor3DUp.y(), anchor3DUp.z(),
                direction, right, up);
        float targetLookX = anchor3DPosition.x() + up[0] * lookHeight;
        float targetLookY = anchor3DPosition.y() + up[1] * lookHeight;
        float targetLookZ = anchor3DPosition.z() + up[2] * lookHeight;
        float targetCameraX = anchor3DPosition.x() + up[0] * heightOffset + right[0] * shoulderOffset
                - direction[0] * distance;
        float targetCameraY = anchor3DPosition.y() + up[1] * heightOffset + right[1] * shoulderOffset
                - direction[1] * distance;
        float targetCameraZ = anchor3DPosition.z() + up[2] * heightOffset + right[2] * shoulderOffset
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
    }
}
