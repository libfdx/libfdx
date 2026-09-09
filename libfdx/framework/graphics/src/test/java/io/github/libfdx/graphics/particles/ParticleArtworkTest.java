package io.github.libfdx.graphics.particles;

import io.github.libfdx.core.FdxException;
import org.junit.jupiter.api.Test;
import java.nio.ByteBuffer;
import static org.junit.jupiter.api.Assertions.*;

final class ParticleArtworkTest {
    @Test void spritesHaveTransparentBordersAndDistinctArtwork() {
        int previousHash = 0;
        for (ParticleSprite sprite : ParticleSprite.values()) {
            ByteBuffer pixels = sprite.pixels(128);
            assertEquals(128 * 128 * 4, pixels.remaining());
            int coverage = 0;
            for (int y = 0; y < 128; y++) for (int x = 0; x < 128; x++) {
                int alpha = pixels.get((y * 128 + x) * 4 + 3) & 255;
                if (x == 0 || y == 0 || x == 127 || y == 127) assertEquals(0, alpha, sprite.name());
                if (alpha > 128) coverage++;
            }
            assertTrue(coverage > 40 && coverage < 14000, sprite.name());
            assertNotEquals(previousHash, pixels.hashCode());
            previousHash = pixels.hashCode();
        }
        // A disc has a solid core rather than a broad blurry gradient.
        ByteBuffer disc = ParticleSprite.DISC.pixels(128);
        assertEquals(255, disc.get((64 * 128 + 96) * 4 + 3) & 255);
        assertThrows(FdxException.class, () -> ParticleSprite.DISC.pixels(0));
    }

    @Test void curvesCopyKeysAndValidateTimes() {
        float[] keys = {0, 0, 0.25f, 1, 1, 0};
        ParticleCurve curve = new ParticleCurve(keys);
        keys[3] = 0;
        assertEquals(0.5f, curve.sample(0.125f));
        assertEquals(1, curve.sample(0.25f));
        assertEquals(0, curve.sample(2));
        assertThrows(FdxException.class, () -> new ParticleCurve(0, 0, 0, 1));
        assertThrows(FdxException.class, () -> new ParticleCurve(0, 0, 1, Float.NaN));
    }
}
