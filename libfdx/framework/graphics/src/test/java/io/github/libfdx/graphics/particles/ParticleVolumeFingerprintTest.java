package io.github.libfdx.graphics.particles;

import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Random;

class ParticleVolumeFingerprintTest {
    static String[] fingerprints() throws Exception {
        ParticleVolume volume = new ParticleVolume(16, 24, 8);
        Random random = new Random(71234);
        String[] result = new String[6];
        for (int frame = 0; frame < 3; frame++) {
            volume.bounds(-2 + frame * 0.25f, -3, -1, 4 + frame, 6, 2);
            for (int particle = 0; particle < 64; particle++) {
                volume.add(
                        random.nextFloat() * 6 - 3,
                        random.nextFloat() * 8 - 4,
                        random.nextFloat() * 4 - 2,
                        0.01f + random.nextFloat() * 1.5f,
                        random.nextFloat() * 3,
                        random.nextFloat(),
                        ParticleVolume.Medium.values()[particle & 3]);
            }
            volume.add(0, 0, 0, 0.25f, 0, 0.5f, ParticleVolume.Medium.FIRE);
            volume.add(100, 100, 100, 0.1f, 1, 0.5f, ParticleVolume.Medium.SNOW);
            result[frame * 2] = fingerprint(volume);
            volume.clear();
            result[frame * 2 + 1] = fingerprint(volume);
        }
        return result;
    }

    private static String fingerprint(ParticleVolume volume) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        for (float value : volume.field) {
            int bits = Float.floatToRawIntBits(value);
            digest.update((byte) (bits >>> 24));
            digest.update((byte) (bits >>> 16));
            digest.update((byte) (bits >>> 8));
            digest.update((byte) bits);
        }
        ByteBuffer pixels = volume.pack();
        for (int i = 0; i < pixels.limit(); i++) digest.update(pixels.get(i));
        return HexFormat.of().formatHex(digest.digest());
    }

    @org.junit.jupiter.api.Test
    void depositedFloatBitsAndPackedPixelsMatchEstablishedFixtures() throws Exception {
        // Fingerprints captured before loop-invariant extraction, including clears and bounds
        // changes.
        org.junit.jupiter.api.Assertions.assertArrayEquals(
                new String[] {
                    "1c6e8b137631cf32068c4ec4e9c5ba580dbd72beabc041e15330a64ac94e44e0",
                    "0693f6bfa2117a9b14f9ceca13d3a5611de5dca226bf999f20a7f615fbd08dff",
                    "20025258d04c0e8b9affda1714edfef086a68a70f97d9416c1ba17799a123df2",
                    "0693f6bfa2117a9b14f9ceca13d3a5611de5dca226bf999f20a7f615fbd08dff",
                    "da0dfbb5a740a3a29ab94e99f57b73223fc3c13014e4dca5e96c061244692f42",
                    "0693f6bfa2117a9b14f9ceca13d3a5611de5dca226bf999f20a7f615fbd08dff"
                },
                fingerprints());
    }
}
