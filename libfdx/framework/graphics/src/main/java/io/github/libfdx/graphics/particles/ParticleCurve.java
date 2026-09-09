package io.github.libfdx.graphics.particles;

import io.github.libfdx.core.FdxException;

/** Immutable piecewise-linear lifetime curve. Values and times are in [0,1]. */
public final class ParticleCurve {
    public static final ParticleCurve LINEAR = new ParticleCurve(0, 0, 1, 1);
    public static final ParticleCurve FADE = new ParticleCurve(0, 0, 0.12f, 1, 0.55f, 0.8f, 1, 0);
    private final float[] keys;

    /** Copies alternating time/value pairs; times must increase from zero to one. */
    public ParticleCurve(float... timeValuePairs) {
        if (timeValuePairs == null || timeValuePairs.length < 4 || timeValuePairs.length % 2 != 0) {
            throw new FdxException("Particle curve requires at least two time/value pairs");
        }
        keys = timeValuePairs.clone();
        for (int i = 0; i < keys.length; i++) {
            if (!Float.isFinite(keys[i]) || keys[i] < 0 || keys[i] > 1
                    || (i > 0 && i % 2 == 0 && keys[i] <= keys[i - 2])) {
                throw new FdxException("Particle curve requires increasing times and finite values in [0,1]");
            }
        }
        if (keys[0] != 0 || keys[keys.length - 2] != 1) {
            throw new FdxException("Particle curve must start at time zero and end at time one");
        }
    }

    /** Samples without allocation, clamping finite times outside [0,1]. */
    public float sample(float time) {
        if (!Float.isFinite(time)) throw new FdxException("Particle curve time must be finite");
        if (time <= 0) return keys[1];
        for (int i = 2; i < keys.length; i += 2) {
            if (time <= keys[i]) {
                float t = (time - keys[i - 2]) / (keys[i] - keys[i - 2]);
                return keys[i - 1] + (keys[i + 1] - keys[i - 1]) * t;
            }
        }
        return keys[keys.length - 1];
    }
}
