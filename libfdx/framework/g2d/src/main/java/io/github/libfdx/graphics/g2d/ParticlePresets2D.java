package io.github.libfdx.graphics.g2d;

import io.github.libfdx.core.FdxException;
import io.github.libfdx.graphics.particles.ParticleCurve;

/**
 * Starting points in world units (Y up). Each method creates a caller-owned CPU emitter.
 * Scale converts one effect unit to your world units, e.g. 100 for pixel coordinates.
 * Chain emitter setters to customize; render with matching ParticleSprite artwork or your own texture.
 */
public final class ParticlePresets2D {
    private ParticlePresets2D() {}

    /** Rising tapered flames; pair with ParticleSprite.FLAME. */
    public static ParticleEmitter2D fire(int capacity, float scale) {
        return base(capacity, scale).emissionRate(90).lifetime(0.65f, 1.15f)
                .speed(0.55f * scale, 1.05f * scale).direction(90, 24)
                .gravity(0, 0.3f * scale).spawnArea(0.32f * scale, 0.04f * scale)
                .size(0.35f * scale, 0.55f * scale, 0.04f * scale, 0.09f * scale)
                .color(1, 0.85f, 0.3f, 0.95f, 0.9f, 0.12f, 0.015f, 0)
                .rotation(-12, 12, -18, 18).aspectRatio(0.5f).drag(0.3f)
                .curves(new ParticleCurve(0, 0.45f, 0.18f, 0, 1, 1),
                        new ParticleCurve(0, 0, 0.55f, 0.2f, 1, 1), ParticleCurve.FADE);
    }

    /** Expanding, slowly drifting smoke; pair with ParticleSprite.SMOKE. */
    public static ParticleEmitter2D smoke(int capacity, float scale) {
        return base(capacity, scale).emissionRate(18).lifetime(2.2f, 3.4f)
                .speed(0.28f * scale, 0.45f * scale).direction(90, 32)
                .gravity(0.045f * scale, 0.08f * scale)
                .spawnArea(0.25f * scale, 0.06f * scale)
                .size(0.22f * scale, 0.35f * scale, 0.65f * scale, 0.95f * scale)
                .color(0.42f, 0.44f, 0.48f, 0.6f, 0.65f, 0.68f, 0.72f, 0)
                .rotation(-180, 180, -25, 25).drag(0.4f);
    }

    /** Small ballistic embers; pair with ParticleSprite.SPARK. */
    public static ParticleEmitter2D sparks(int capacity, float scale) {
        return base(capacity, scale).emissionRate(28).lifetime(0.7f, 1.4f)
                .speed(0.8f * scale, 1.8f * scale).direction(90, 65)
                .gravity(0, -1.1f * scale).size(0.10f * scale, 0.025f * scale)
                .color(1, 0.9f, 0.35f, 1, 1, 0.22f, 0.02f, 0).rotation(-25, 25, -60, 60);
    }

    /** Falling snow over a broad area; pair with ParticleSprite.SNOW. */
    public static ParticleEmitter2D snow(int capacity, float scale) {
        return base(capacity, scale).emissionRate(25).lifetime(3, 5)
                .speed(0.2f * scale, 0.4f * scale).direction(270, 18)
                .spawnArea(1.6f * scale, 0)
                .size(0.09f * scale, 0.14f * scale, 0.07f * scale, 0.11f * scale)
                .color(0.85f, 0.93f, 1, 1, 0.85f, 0.93f, 1, 0).rotation(-180, 180, -65, 65);
    }

    /** Dense turbulent fire for emitter.deposit and ParticleVolumeRenderer. */
    public static ParticleEmitter2D volumetricFire(int capacity, float scale) {
        return fire(capacity, scale).emissionRate(420).lifetime(0.9f, 1.6f)
                .speed(0.7f * scale, 1.3f * scale)
                .size(0.17f * scale, 0.28f * scale, 0.15f * scale, 0.28f * scale)
                .turbulence(0.8f * scale, 5 / scale).drag(0.7f)
                .curves(ParticleCurve.LINEAR, ParticleCurve.LINEAR, ParticleCurve.FADE);
    }

    private static ParticleEmitter2D base(int capacity, float scale) {
        if (!Float.isFinite(scale) || scale <= 0) throw new FdxException("Particle scale must be finite and positive");
        return new ParticleEmitter2D(capacity).curves(ParticleCurve.LINEAR, ParticleCurve.LINEAR, ParticleCurve.FADE);
    }
}
