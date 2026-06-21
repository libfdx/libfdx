package io.github.libfdx.graphics.g3d;

import io.github.libfdx.core.FdxException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class ParticleEmitter3DTest {
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
