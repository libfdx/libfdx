package io.github.libfdx.graphics.g3d;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The PBR texture-coordinate channel used to be allocated and handed to the
 * mesh without ever being written, so a generated primitive sampled texel
 * (0, 0) everywhere and a base-colour texture rendered as one flat colour.
 */
class ModelBuilderTextureCoordinatesTest {

    @Test
    void sphericalProjectionMapsAPointOnEachAxis() {
        // +X, -X, +Y (north pole), -Y (south pole)
        float[] positions = {
                1.0f, 0.0f, 0.0f,
                -1.0f, 0.0f, 0.0f,
                0.0f, 1.0f, 0.0f,
                0.0f, -1.0f, 0.0f};
        float[] uv = new float[positions.length / 3 * 2];
        ModelBuilder.generateSphericalTextureCoordinates(positions, uv);

        assertEquals(0.5f, uv[0], 1.0e-5f, "+X longitude");
        assertEquals(0.5f, uv[1], 1.0e-5f, "+X is on the equator");
        assertEquals(0.5f, uv[3], 1.0e-5f, "-X is on the equator");
        assertEquals(0.0f, uv[5], 1.0e-5f, "+Y is the north pole");
        assertEquals(1.0f, uv[7], 1.0e-5f, "-Y is the south pole");
    }

    /**
     * Longitude wraps at the back of the sphere. A triangle straddling that
     * seam must not interpolate u backwards from ~1 to ~0, which would smear
     * the entire texture across it.
     */
    @Test
    void seamTrianglesAreNotInterpolatedBackwards() {
        // Three vertices hugging the -Z/-X seam, on both sides of the wrap.
        float[] positions = {
                -1.0f, 0.0f, 0.05f,
                -1.0f, 0.0f, -0.05f,
                -0.95f, 0.1f, -0.02f};
        float[] uv = new float[6];
        ModelBuilder.generateSphericalTextureCoordinates(positions, uv);

        float min = Math.min(uv[0], Math.min(uv[2], uv[4]));
        float max = Math.max(uv[0], Math.max(uv[2], uv[4]));
        assertTrue(max - min <= 0.5f,
                "seam triangle must span a short u range after repair, got "
                        + min + ".." + max);
    }

    @Test
    void ordinaryTrianglesAreLeftAlone() {
        float[] positions = {
                1.0f, 0.0f, 0.0f,
                0.9f, 0.1f, 0.1f,
                0.9f, -0.1f, 0.1f};
        float[] uv = new float[6];
        ModelBuilder.generateSphericalTextureCoordinates(positions, uv);
        for(int i = 0; i < 6; i += 2) {
            assertTrue(uv[i] >= 0.0f && uv[i] <= 1.0f,
                    "u should stay in 0..1 away from the seam: " + uv[i]);
        }
    }
}
