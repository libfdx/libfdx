package io.github.libfdx.graphics.g3d;

import io.github.libfdx.core.FdxException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class ParticleEmitter3DTest {
    @Test
    void turbulentVolumePresetExpiresAndAcceptsCustomMedium() {
        ParticleEmitter3D emitter = ParticlePresets3D.volumetricFire(800,1).seed(19);
        for(int i=0;i<180;i++) emitter.update(1f/60);
        io.github.libfdx.graphics.particles.ParticleVolume volume =
                new io.github.libfdx.graphics.particles.ParticleVolume(16,16,16).bounds(-2,-1,-2,4,4,4);
        emitter.deposit(volume,io.github.libfdx.graphics.particles.ParticleVolume.Medium.FIRE);
        org.junit.jupiter.api.Assertions.assertTrue(emitter.activeCount()>100);
        emitter.emissionRate(0).update(2);
        assertEquals(0,emitter.activeCount());
        assertThrows(FdxException.class,()->emitter.turbulence(-1,1));
        assertThrows(FdxException.class,()->emitter.turbulence(1,0));
    }

    @Test
    void customCurvesControlGrowthAndFadeIn() {
        ParticleEmitter3D emitter = new ParticleEmitter3D(1).lifetime(2f).size(1, 3)
                .color(1, 1, 1, 1, 1, 1, 1, 1)
                .curves(new io.github.libfdx.graphics.particles.ParticleCurve(0, 0, 0.5f, 1, 1, 0),
                        io.github.libfdx.graphics.particles.ParticleCurve.LINEAR,
                        io.github.libfdx.graphics.particles.ParticleCurve.FADE);
        emitter.emit(1);
        assertEquals(0, emitter.alpha(0));
        emitter.update(1);
        assertEquals(3, emitter.size(0));
        org.junit.jupiter.api.Assertions.assertTrue(emitter.alpha(0) > 0.8f);
        emitter.update(0.5f);
        assertEquals(2, emitter.size(0));
    }

    @Test
    void spawnBoundsAndDragAreDeterministic() {
        ParticleEmitter3D a = new ParticleEmitter3D(32).seed(42).spawnArea(4, 2, 6).lifetime(5f)
                .speed(2).direction(1, 0, 0, 0).drag(1);
        ParticleEmitter3D b = new ParticleEmitter3D(32).seed(42).spawnArea(4, 2, 6).lifetime(5f)
                .speed(2).direction(1, 0, 0, 0).drag(1);
        a.emit(32); b.emit(32);
        for (int i = 0; i < 32; i++) {
            assertEquals(a.x(i), b.x(i));
            org.junit.jupiter.api.Assertions.assertTrue(Math.abs(a.x(i)) <= 2 && Math.abs(a.y(i)) <= 1);
            org.junit.jupiter.api.Assertions.assertTrue(Math.abs(a.z(i)) <= 3);
        }
        float x = a.x(0);
        a.update(1);
        assertEquals(x + 2 * (float)Math.exp(-1), a.x(0), 0.00001f);
        assertThrows(FdxException.class, () -> a.drag(-1));
        assertThrows(FdxException.class, () -> a.aspectRatio(0));
        assertThrows(FdxException.class, () -> a.spawnArea(-1, 0, 0));
    }

    @Test
    void presetsCanBeCustomizedAndRemainCapacityBounded() {
        ParticleEmitter3D[] effects = { ParticlePresets3D.fire(64, 1), ParticlePresets3D.smoke(64, 1),
                ParticlePresets3D.sparks(64, 1), ParticlePresets3D.snow(64, 1) };
        for (ParticleEmitter3D effect : effects) {
            effect.emissionRate(50).aspectRatio(0.5f);
            for (int frame = 0; frame < 600; frame++) effect.update(1f / 60);
            org.junit.jupiter.api.Assertions.assertTrue(effect.activeCount() > 0 && effect.activeCount() <= 64);
            effect.emissionRate(0).update(6);
            assertEquals(0, effect.activeCount());
        }
        assertThrows(FdxException.class, () -> ParticlePresets3D.fire(1, Float.NaN));
    }

    @Test
    void emitsUpdatesAndExpiresParticlesWithoutParticleObjects() {
        ParticleEmitter3D emitter = new ParticleEmitter3D(4)
                .seed(12)
                .position(1.0f, 2.0f, 3.0f)
                .lifetime(1.0f)
                .speed(0.0f)
                .size(0.2f, 0.1f)
                .color(1.0f, 0.25f, 0.0f, 1.0f, 0.0f, 0.0f, 1.0f, 0.0f);

        assertEquals(3, emitter.emit(3));
        assertEquals(3, emitter.activeCount());
        assertEquals(1.0f, emitter.x(0));
        assertEquals(2.0f, emitter.y(0));
        assertEquals(3.0f, emitter.z(0));

        emitter.update(0.5f);

        assertEquals(3, emitter.activeCount());
        assertEquals(0.5f, emitter.age(0));
        assertEquals(0.15f, emitter.size(0), 0.0001f);
        assertEquals(0.5f, emitter.red(0), 0.0001f);
        assertEquals(0.5f, emitter.blue(0), 0.0001f);
        assertEquals(0.5f, emitter.alpha(0), 0.0001f);

        emitter.update(0.51f);

        assertEquals(0, emitter.activeCount());
    }

    @Test
    void directedEmissionAndGravityMoveParticlesIn3d() {
        ParticleEmitter3D emitter = new ParticleEmitter3D(1)
                .seed(3)
                .position(0.0f, 0.0f, 0.0f)
                .lifetime(2.0f)
                .speed(2.0f)
                .direction(0.0f, 1.0f, 0.0f, 0.0f)
                .gravity(0.0f, -1.0f, 0.0f);

        assertEquals(1, emitter.emit(1));
        emitter.update(0.5f);

        assertEquals(0.0f, emitter.x(0), 0.0001f);
        assertEquals(0.75f, emitter.y(0), 0.0001f);
        assertEquals(0.0f, emitter.z(0), 0.0001f);
    }

    @Test
    void emissionRateUsesAccumulatorAndCapacity() {
        ParticleEmitter3D emitter = new ParticleEmitter3D(2)
                .seed(4)
                .emissionRate(8.0f)
                .lifetime(2.0f)
                .speed(0.0f);

        emitter.update(0.125f);
        assertEquals(1, emitter.activeCount());

        emitter.update(0.25f);
        assertEquals(2, emitter.activeCount());
        assertEquals(0, emitter.emit(1));

        emitter.clear();
        assertEquals(0, emitter.activeCount());
    }

    @Test
    void rejectsInvalidConfiguration() {
        ParticleEmitter3D emitter = new ParticleEmitter3D(1);

        assertThrows(FdxException.class, () -> new ParticleEmitter3D(0));
        assertThrows(FdxException.class, () -> emitter.position(Float.NaN, 0.0f, 0.0f));
        assertThrows(FdxException.class, () -> emitter.emissionRate(-1.0f));
        assertThrows(FdxException.class, () -> emitter.lifetime(0.0f));
        assertThrows(FdxException.class, () -> emitter.speed(-1.0f));
        assertThrows(FdxException.class, () -> emitter.direction(0.0f, 0.0f, 0.0f, 0.0f));
        assertThrows(FdxException.class, () -> emitter.direction(0.0f, 1.0f, 0.0f, -1.0f));
        assertThrows(FdxException.class, () -> emitter.gravity(Float.POSITIVE_INFINITY, 0.0f, 0.0f));
        assertThrows(FdxException.class, () -> emitter.size(0.0f, 1.0f));
        assertThrows(FdxException.class, () -> emitter.color(1.1f, 0.0f, 0.0f, 1.0f,
                1.0f, 1.0f, 1.0f, 0.0f));
        assertThrows(FdxException.class, () -> emitter.update(-0.01f));
        assertThrows(FdxException.class, () -> emitter.emit(-1));
        assertThrows(IndexOutOfBoundsException.class, () -> emitter.x(0));
    }
}
