package io.github.libfdx.testsupport.graphics;

import java.util.Arrays;

/** Persistent exploration with optional, smoothly dimmed memory outside the player's current sight. */
public final class FogExploration {
    static final int WIDTH = 48, DEPTH = 40, DENSITY = 8;
    static final int COLUMNS = WIDTH * DENSITY + 1, ROWS = DEPTH * DENSITY + 1;
    static final float RADIUS = 5.6f, FEATHER = 1.1f;
    final float[] opacity = new float[COLUMNS * ROWS];
    private final float[] target = new float[opacity.length];
    private final float[] sight = new float[opacity.length];
    private float lastX, lastZ;
    private boolean hasPrevious, dimExplored;
    int revision;

    public FogExploration() { reset(); }

    public void reset() {
        Arrays.fill(opacity, 1); Arrays.fill(target, 1);
        Arrays.fill(sight, 1);
        hasPrevious = false; revision++;
    }

    public void dimExplored(boolean enabled) { dimExplored = enabled; }

    /** Least fog opacity across a conservative model footprint, including mask filtering support. */
    public float minimumOpacity(float x, float z, float radius) {
        return footprintOpacity(x, z, radius, false);
    }

    /** Greatest opacity over the same filtered footprint; used only to skip fully solid layers. */
    public float maximumOpacity(float x, float z, float radius) {
        return footprintOpacity(x, z, radius, true);
    }

    private float footprintOpacity(float x, float z, float radius, boolean maximum) {
        int left = Math.max(0, Math.min(COLUMNS - 1,
                (int)Math.floor((x - radius + WIDTH / 2f) * DENSITY) - 1));
        int right = Math.max(0, Math.min(COLUMNS - 1,
                (int)Math.ceil((x + radius + WIDTH / 2f) * DENSITY) + 1));
        int top = Math.max(0, Math.min(ROWS - 1,
                (int)Math.floor((z - radius + DEPTH / 2f) * DENSITY) - 1));
        int bottom = Math.max(0, Math.min(ROWS - 1,
                (int)Math.ceil((z + radius + DEPTH / 2f) * DENSITY) + 1));
        float result = maximum ? 0 : 1;
        for (int row = top; row <= bottom; row++) for (int col = left; col <= right; col++) {
            float value = opacity[row * COLUMNS + col];
            result = maximum ? Math.max(result, value) : Math.min(result, value);
            if (result == (maximum ? 1 : 0)) return result;
        }
        return result;
    }

    public void reveal(float x, float z) {
        if (hasPrevious && x == lastX && z == lastZ) return;
        float ax = hasPrevious ? lastX : x, az = hasPrevious ? lastZ : z;
        // Paint the swept segment, so a slow frame cannot leave disconnected circles.
        int left = Math.max(0, (int)Math.floor((Math.min(ax, x) - RADIUS + WIDTH / 2f) * DENSITY));
        int right = Math.min(COLUMNS - 1, (int)Math.ceil((Math.max(ax, x) + RADIUS + WIDTH / 2f) * DENSITY));
        int top = Math.max(0, (int)Math.floor((Math.min(az, z) - RADIUS + DEPTH / 2f) * DENSITY));
        int bottom = Math.min(ROWS - 1, (int)Math.ceil((Math.max(az, z) + RADIUS + DEPTH / 2f) * DENSITY));
        float vx = x - ax, vz = z - az, length2 = vx * vx + vz * vz;
        for (int row = top; row <= bottom; row++) for (int col = left; col <= right; col++) {
            float dx = col / (float)DENSITY - WIDTH / 2f - ax;
            float dz = row / (float)DENSITY - DEPTH / 2f - az;
            float t = length2 > 0 ? Math.max(0, Math.min(1, (dx * vx + dz * vz) / length2)) : 0;
            dx -= t * vx; dz -= t * vz;
            float edge = Math.max(0, Math.min(1, ((float)Math.sqrt(dx * dx + dz * dz) - RADIUS + FEATHER) / FEATHER));
            int index = row * COLUMNS + col;
            target[index] = Math.min(target[index], edge * edge * (3 - 2 * edge));
            // Current sight follows only the player, while target remembers the entire swept path.
            float sx = col / (float)DENSITY - WIDTH / 2f - x;
            float sz = row / (float)DENSITY - DEPTH / 2f - z;
            float sightEdge = Math.max(0, Math.min(1,
                    ((float)Math.sqrt(sx * sx + sz * sz) - RADIUS + FEATHER) / FEATHER));
            sight[index] = sightEdge * sightEdge * (3 - 2 * sightEdge);
        }
        lastX = x; lastZ = z; hasPrevious = true;
    }

    public void update(float delta) {
        if (delta <= 0) return;
        // Exponential response is frame-rate independent, settling in about 0.2 seconds.
        float blend = 1 - (float)Math.exp(-Math.max(0, delta) * 18);
        boolean changed = false;
        for (int i = 0; i < opacity.length; i++) {
            float desired = desiredOpacity(i);
            float difference = opacity[i] - desired;
            if (difference != 0) {
                opacity[i] = Math.abs(difference) < .0001f ? desired : opacity[i] - difference * blend;
                changed = true;
            }
        }
        if (changed) revision++;
    }

    private float desiredOpacity(int index) {
        return dimExplored ? Math.max(target[index], .5f * sight[index]) : target[index];
    }

    public void settle() {
        for (int i = 0; i < opacity.length; i++) opacity[i] = desiredOpacity(i);
        revision++;
    }

}
