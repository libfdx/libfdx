package io.github.libfdx.testsupport.graphics;

/** Swept movement for the cube; manual controls and the patrol share all collision rules. */
public final class FogMovement {
    static final float HALF_PLAYER = .45f;
    private final float[] left = new float[160], right = new float[160], top = new float[160], bottom = new float[160];
    private int count;
    public float x = 0, z = 12;

    public void obstacle(float x, float z, float halfWidth, float halfDepth) {
        left[count] = x - halfWidth - HALF_PLAYER; right[count] = x + halfWidth + HALF_PLAYER;
        top[count] = z - halfDepth - HALF_PLAYER; bottom[count++] = z + halfDepth + HALF_PLAYER;
    }

    public void move(float dx, float dz) {
        int steps = Math.max(1, (int)Math.ceil(Math.max(Math.abs(dx), Math.abs(dz)) / .1f));
        dx /= steps; dz /= steps;
        for (int i = 0; i < steps; i++) {
            float nextX = Math.max(-23, Math.min(23, x + dx));
            if (free(nextX, z)) x = nextX;
            float nextZ = Math.max(-19, Math.min(19, z + dz));
            if (free(x, nextZ)) z = nextZ;
        }
    }

    boolean free(float x, float z) {
        for (int i = 0; i < count; i++)
            if (x > left[i] && x < right[i] && z > top[i] && z < bottom[i]) return false;
        return true;
    }
}
