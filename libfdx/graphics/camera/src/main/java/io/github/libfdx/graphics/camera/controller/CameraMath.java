package io.github.libfdx.graphics.camera.controller;

final class CameraMath {
    private CameraMath() {
    }

    static float clamp(float value, float min, float max) {
        if (value < min) {
            return min;
        }
        return value > max ? max : value;
    }

    static float damping(float damping, float deltaSeconds) {
        if (damping <= 0.0f) {
            return 1.0f;
        }
        return 1.0f - (float)Math.exp(-damping * Math.max(0.0f, deltaSeconds));
    }

    static float normalize(float[] vector) {
        float len = (float)Math.sqrt(vector[0] * vector[0] + vector[1] * vector[1] + vector[2] * vector[2]);
        if (len <= 0.000001f) {
            vector[0] = 0.0f;
            vector[1] = 1.0f;
            vector[2] = 0.0f;
            return 0.0f;
        }
        float inv = 1.0f / len;
        vector[0] *= inv;
        vector[1] *= inv;
        vector[2] *= inv;
        return len;
    }

    static void cross(float ax, float ay, float az, float bx, float by, float bz, float[] out) {
        out[0] = ay * bz - az * by;
        out[1] = az * bx - ax * bz;
        out[2] = ax * by - ay * bx;
    }

    static void directionFromAngles(float yawRadians, float pitchRadians, float upX, float upY, float upZ,
            float[] directionOut, float[] rightOut, float[] upOut) {
        upOut[0] = upX;
        upOut[1] = upY;
        upOut[2] = upZ;
        if (normalize(upOut) == 0.0f) {
            upOut[0] = 0.0f;
            upOut[1] = 1.0f;
            upOut[2] = 0.0f;
        }

        float baseX = 0.0f;
        float baseY = 0.0f;
        float baseZ = -1.0f;
        float dot = baseX * upOut[0] + baseY * upOut[1] + baseZ * upOut[2];
        baseX -= upOut[0] * dot;
        baseY -= upOut[1] * dot;
        baseZ -= upOut[2] * dot;
        float baseLen = (float)Math.sqrt(baseX * baseX + baseY * baseY + baseZ * baseZ);
        if (baseLen <= 0.0001f) {
            baseX = 1.0f;
            baseY = 0.0f;
            baseZ = 0.0f;
            dot = baseX * upOut[0] + baseY * upOut[1] + baseZ * upOut[2];
            baseX -= upOut[0] * dot;
            baseY -= upOut[1] * dot;
            baseZ -= upOut[2] * dot;
            baseLen = (float)Math.sqrt(baseX * baseX + baseY * baseY + baseZ * baseZ);
        }
        float invBaseLen = 1.0f / baseLen;
        baseX *= invBaseLen;
        baseY *= invBaseLen;
        baseZ *= invBaseLen;

        cross(baseX, baseY, baseZ, upOut[0], upOut[1], upOut[2], rightOut);
        normalize(rightOut);

        float cosYaw = (float)Math.cos(yawRadians);
        float sinYaw = (float)Math.sin(yawRadians);
        float yawForwardX = baseX * cosYaw - rightOut[0] * sinYaw;
        float yawForwardY = baseY * cosYaw - rightOut[1] * sinYaw;
        float yawForwardZ = baseZ * cosYaw - rightOut[2] * sinYaw;
        float cosPitch = (float)Math.cos(pitchRadians);
        float sinPitch = (float)Math.sin(pitchRadians);
        directionOut[0] = yawForwardX * cosPitch - upOut[0] * sinPitch;
        directionOut[1] = yawForwardY * cosPitch - upOut[1] * sinPitch;
        directionOut[2] = yawForwardZ * cosPitch - upOut[2] * sinPitch;
        normalize(directionOut);
        cross(directionOut[0], directionOut[1], directionOut[2], upOut[0], upOut[1], upOut[2], rightOut);
        normalize(rightOut);
    }
}
