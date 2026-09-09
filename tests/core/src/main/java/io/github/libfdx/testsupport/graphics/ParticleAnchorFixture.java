package io.github.libfdx.testsupport.graphics;

import io.github.libfdx.core.FdxException;
import io.github.libfdx.graphics.camera.Camera;
import io.github.libfdx.graphics.particles.ParticleSolidRenderer;
import io.github.libfdx.math.Vector3;
import java.nio.ByteBuffer;

/** Frozen asymmetric geometry for checking world anchoring against actual GPU pixels. */
public final class ParticleAnchorFixture {
    private static final float[][] CENTERS = {{-1, 0.2f, -1}, {0.4f, 0.8f, -0.3f}, {1, 0.3f, -1.8f}};

    public static void draw(ParticleSolidRenderer solids) {
        for (int i = 0; i < CENTERS.length; i++) {
            float[] p = CENTERS[i];
            solids.add(p[0], p[1], p[2], 0.12f, i == 0 ? 1 : 0, i == 1 ? 1 : 0, i == 2 ? 1 : 0, 1);
        }
        // Must remain hidden beneath the opaque floor from every fixture camera.
        solids.add(0, -0.85f, -1.2f, 0.12f, 1, 0, 1, 1);
    }

    public static void validate(Camera camera, int width, int height, ByteBuffer pixels) {
        long[] count = new long[3], sumX = new long[3], sumY = new long[3];
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
            int offset = (y * width + x) * 4;
            int r = pixels.get(offset) & 255, g = pixels.get(offset + 1) & 255, b = pixels.get(offset + 2) & 255;
            if (r > 80 && b > 80 && g < 20) throw new FdxException("Particle behind floor leaked through depth test");
            int color = r > 80 && g < 20 && b < 20 ? 0 : g > 80 && r < 20 && b < 20 ? 1
                    : b > 80 && r < 20 && g < 20 ? 2 : -1;
            if (color >= 0) { count[color]++; sumX[color] += x; sumY[color] += y; }
        }
        for (int i = 0; i < CENTERS.length; i++) {
            float[] p = CENTERS[i];
            Vector3 expected = camera.combined().transformProjective(new Vector3(p[0], p[1], p[2]));
            double x = (expected.x() + 1) * width / 2, y = (expected.y() + 1) * height / 2;
            double actualX = (double)sumX[i] / count[i] + 0.5, actualY = (double)sumY[i] / count[i] + 0.5;
            if (count[i] < 20 || Math.abs(actualX - x) > 2 || Math.abs(actualY - y) > 2) {
                throw new FdxException("Particle anchor " + i + " expected (" + x + ", " + y
                        + ") but rendered at (" + actualX + ", " + actualY + "), pixels=" + count[i]);
            }
        }
    }
}
