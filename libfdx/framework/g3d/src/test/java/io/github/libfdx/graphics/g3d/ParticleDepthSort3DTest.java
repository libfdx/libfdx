package io.github.libfdx.graphics.g3d;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class ParticleDepthSort3DTest {
    @Test
    void ordersFarToNearWithDeterministicTiesAndReusesStorage() {
        float[] depths = { 2, -1, 8, 2, 4 };
        int[] order = new int[5];
        ParticleDepthSort3D.sort(depths, order, 5);
        assertArrayEquals(new int[] { 2, 4, 0, 3, 1 }, order);
        ParticleDepthSort3D.sort(depths, order, 2);
        assertEquals(0, order[0]);
        assertEquals(1, order[1]);
        ParticleDepthSort3D.sort(depths, order, 0);
    }
}
