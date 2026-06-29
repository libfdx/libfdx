package io.github.libfdx.graphics.camera.controller;

import io.github.libfdx.core.FdxException;

/**
 * Spline-smoothed, distance-paced keyframe path for 3D cinematic cameras.
 *
 * @author xpenatan
 */
public final class KeyframeCinematicCameraPath3D implements CinematicCameraPath3D {
    private static final int ARC_LENGTH_SAMPLES_PER_SEGMENT = 64;
    private final float durationSeconds;
    private final float[] cameraPoints;
    private final float[] lookAtPoints;
    private final float[] upPoints;
    private final float[] cumulativeCameraDistances;
    private final int pointCount;
    private final int segmentCount;
    private final boolean closedCameraPath;
    private final boolean closedLookAtPath;
    private final boolean closedUpPath;
    private final float totalCameraDistance;
    private boolean loop = true;

    public KeyframeCinematicCameraPath3D(float durationSeconds, float[] cameraPoints, float[] lookAtPoints) {
        this(durationSeconds, cameraPoints, lookAtPoints, null);
    }

    public KeyframeCinematicCameraPath3D(float durationSeconds, float[] cameraPoints, float[] lookAtPoints,
            float[] upPoints) {
        if (!Float.isFinite(durationSeconds) || durationSeconds <= 0.0f) {
            throw new FdxException("KeyframeCinematicCameraPath3D duration must be greater than zero");
        }
        validatePoints(cameraPoints, "camera");
        validatePoints(lookAtPoints, "look-at");
        if (cameraPoints.length != lookAtPoints.length) {
            throw new FdxException("KeyframeCinematicCameraPath3D camera and look-at point counts must match");
        }
        if (upPoints != null && upPoints.length != cameraPoints.length) {
            throw new FdxException("KeyframeCinematicCameraPath3D up point count must match camera point count");
        }
        this.durationSeconds = durationSeconds;
        this.cameraPoints = cameraPoints.clone();
        this.lookAtPoints = lookAtPoints.clone();
        this.upPoints = upPoints != null ? upPoints.clone() : null;
        pointCount = cameraPoints.length / 3;
        segmentCount = pointCount - 1;
        closedCameraPath = isClosedPath(this.cameraPoints);
        closedLookAtPath = isClosedPath(this.lookAtPoints);
        closedUpPath = this.upPoints != null && isClosedPath(this.upPoints);
        cumulativeCameraDistances = new float[segmentCount * ARC_LENGTH_SAMPLES_PER_SEGMENT + 1];
        totalCameraDistance = buildCumulativeDistances();
    }

    public KeyframeCinematicCameraPath3D loop(boolean loop) {
        this.loop = loop;
        return this;
    }

    public boolean loop() {
        return loop;
    }

    public float durationSeconds() {
        return durationSeconds;
    }

    public int pointCount() {
        return pointCount;
    }

    @Override
    public void sample(float timeSeconds, CinematicCameraPathSample3D out) {
        if (out == null) {
            throw new FdxException("Cinematic camera path sample output cannot be null");
        }
        float normalized = normalizedTime(timeSeconds);
        int segment;
        float t;
        if (totalCameraDistance > 0.000001f) {
            float distance = normalized * totalCameraDistance;
            int interval = intervalForDistance(distance);
            float intervalStart = cumulativeCameraDistances[interval];
            float intervalLength = cumulativeCameraDistances[interval + 1] - intervalStart;
            float intervalT = intervalLength <= 0.000001f ? 0.0f : (distance - intervalStart) / intervalLength;
            float scaled = (interval + CameraMath.clamp(intervalT, 0.0f, 1.0f))
                    / ARC_LENGTH_SAMPLES_PER_SEGMENT;
            segment = Math.min((int)scaled, segmentCount - 1);
            t = scaled - segment;
        }
        else {
            float scaled = normalized * segmentCount;
            segment = Math.min((int)scaled, segmentCount - 1);
            t = scaled - segment;
        }
        t = CameraMath.clamp(t, 0.0f, 1.0f);
        out.camera(
                catmullRom(cameraPoints, segment, t, 0, closedCameraPath),
                catmullRom(cameraPoints, segment, t, 1, closedCameraPath),
                catmullRom(cameraPoints, segment, t, 2, closedCameraPath));
        out.lookAt(
                catmullRom(lookAtPoints, segment, t, 0, closedLookAtPath),
                catmullRom(lookAtPoints, segment, t, 1, closedLookAtPath),
                catmullRom(lookAtPoints, segment, t, 2, closedLookAtPath));
        if (upPoints == null) {
            out.up(0.0f, 1.0f, 0.0f);
            return;
        }
        float upX = catmullRom(upPoints, segment, t, 0, closedUpPath);
        float upY = catmullRom(upPoints, segment, t, 1, closedUpPath);
        float upZ = catmullRom(upPoints, segment, t, 2, closedUpPath);
        float length = (float)Math.sqrt(upX * upX + upY * upY + upZ * upZ);
        if (length <= 0.000001f) {
            out.up(0.0f, 1.0f, 0.0f);
            return;
        }
        float invLength = 1.0f / length;
        out.up(upX * invLength, upY * invLength, upZ * invLength);
    }

    private float normalizedTime(float timeSeconds) {
        if (!Float.isFinite(timeSeconds)) {
            timeSeconds = 0.0f;
        }
        float normalized = timeSeconds / durationSeconds;
        if (loop) {
            normalized = normalized - (float)Math.floor(normalized);
        }
        return CameraMath.clamp(normalized, 0.0f, 1.0f);
    }

    private int intervalForDistance(float distance) {
        if (distance >= totalCameraDistance) {
            return cumulativeCameraDistances.length - 2;
        }
        for (int i = 1; i < cumulativeCameraDistances.length; i++) {
            if (distance <= cumulativeCameraDistances[i]) {
                return i - 1;
            }
        }
        return cumulativeCameraDistances.length - 2;
    }

    private float buildCumulativeDistances() {
        float total = 0.0f;
        cumulativeCameraDistances[0] = 0.0f;
        float previousX = cameraPoints[0];
        float previousY = cameraPoints[1];
        float previousZ = cameraPoints[2];
        for (int i = 1; i < cumulativeCameraDistances.length; i++) {
            float scaled = i / (float)ARC_LENGTH_SAMPLES_PER_SEGMENT;
            int segment = Math.min((int)scaled, segmentCount - 1);
            float t = CameraMath.clamp(scaled - segment, 0.0f, 1.0f);
            float x = catmullRom(cameraPoints, segment, t, 0, closedCameraPath);
            float y = catmullRom(cameraPoints, segment, t, 1, closedCameraPath);
            float z = catmullRom(cameraPoints, segment, t, 2, closedCameraPath);
            float dx = x - previousX;
            float dy = y - previousY;
            float dz = z - previousZ;
            total += (float)Math.sqrt(dx * dx + dy * dy + dz * dz);
            cumulativeCameraDistances[i] = total;
            previousX = x;
            previousY = y;
            previousZ = z;
        }
        return total;
    }

    private float catmullRom(float[] points, int segment, float t, int component, boolean closedPath) {
        int p0 = segment - 1;
        int p1 = segment;
        int p2 = segment + 1;
        int p3 = segment + 2;
        float t0 = 0.0f;
        float t1 = nextParameter(points, p0, p1, t0, closedPath);
        float t2 = nextParameter(points, p1, p2, t1, closedPath);
        float t3 = nextParameter(points, p2, p3, t2, closedPath);
        float time = t1 + CameraMath.clamp(t, 0.0f, 1.0f) * (t2 - t1);

        float v0 = pointValue(points, p0, component, closedPath);
        float v1 = pointValue(points, p1, component, closedPath);
        float v2 = pointValue(points, p2, component, closedPath);
        float v3 = pointValue(points, p3, component, closedPath);

        float a1 = interpolateByParameter(v0, v1, t0, t1, time);
        float a2 = interpolateByParameter(v1, v2, t1, t2, time);
        float a3 = interpolateByParameter(v2, v3, t2, t3, time);
        float b1 = interpolateByParameter(a1, a2, t0, t2, time);
        float b2 = interpolateByParameter(a2, a3, t1, t3, time);
        return interpolateByParameter(b1, b2, t1, t2, time);
    }

    private float nextParameter(float[] points, int left, int right, float current, boolean closedPath) {
        float dx = pointValue(points, right, 0, closedPath) - pointValue(points, left, 0, closedPath);
        float dy = pointValue(points, right, 1, closedPath) - pointValue(points, left, 1, closedPath);
        float dz = pointValue(points, right, 2, closedPath) - pointValue(points, left, 2, closedPath);
        float distance = (float)Math.sqrt(dx * dx + dy * dy + dz * dz);
        return current + Math.max(0.0001f, (float)Math.sqrt(distance));
    }

    private static float interpolateByParameter(float a, float b, float left, float right, float time) {
        float range = right - left;
        if (Math.abs(range) <= 0.000001f) {
            return a;
        }
        float t = (time - left) / range;
        return a + (b - a) * t;
    }

    private float pointValue(float[] points, int index, int component, boolean closedPath) {
        if (closedPath) {
            int uniquePointCount = pointCount - 1;
            int wrapped = index % uniquePointCount;
            return points[(wrapped < 0 ? wrapped + uniquePointCount : wrapped) * 3 + component];
        }
        if (index < 0) {
            return 2.0f * points[component] - points[3 + component];
        }
        if (index >= pointCount) {
            int lastOffset = (pointCount - 1) * 3 + component;
            int previousOffset = (pointCount - 2) * 3 + component;
            return 2.0f * points[lastOffset] - points[previousOffset];
        }
        return points[index * 3 + component];
    }

    private static boolean isClosedPath(float[] points) {
        int lastOffset = points.length - 3;
        return Math.abs(points[0] - points[lastOffset]) <= 0.000001f
                && Math.abs(points[1] - points[lastOffset + 1]) <= 0.000001f
                && Math.abs(points[2] - points[lastOffset + 2]) <= 0.000001f;
    }

    private static void validatePoints(float[] points, String label) {
        if (points == null || points.length < 6 || points.length % 3 != 0) {
            throw new FdxException("KeyframeCinematicCameraPath3D " + label
                    + " points must contain at least two xyz positions");
        }
        for (int i = 0; i < points.length; i++) {
            if (!Float.isFinite(points[i])) {
                throw new FdxException("KeyframeCinematicCameraPath3D " + label + " point values must be finite");
            }
        }
    }
}
