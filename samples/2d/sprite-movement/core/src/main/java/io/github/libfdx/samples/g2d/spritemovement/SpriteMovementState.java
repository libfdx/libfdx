package io.github.libfdx.samples.g2d.spritemovement;

import io.github.libfdx.samples.g2d.spritemovement.input.MovementInput;

/** Harbor coordinates follow the painting, with north at the top. Distances use chart units. */
final class SpriteMovementState {
    static final float WIDTH = 640, HEIGHT = WIDTH * 2 / 3;
    static final float START_X = 275, START_Y = 286;
    static final float SPEED = 86;
    record Dock(String name, float x, float y) { }
    static final Dock[] ROUTE = {
            new Dock("Market quay", 186, 214),
            new Dock("North Light", 493, 159),
            new Dock("Crane wharf", 423, 308)
    };

    // Trace the navigable side of the coast and each pier in the harbor artwork.
    private static final float[] BAY = {
            152,0, 568,0, 549,27, 510,48, 483,73, 469,99, 490,117, 529,121,
            493,135, 493,148, 550,129, 568,146, 612,153, 640,181,
            640,208, 611,226, 585,240, 559,245, 542,264, 520,272, 480,264, 476,270,
            494,292, 479,309, 439,285, 434,298, 470,323, 463,343, 489,359,
            529,377, 553,386, 527,408, 484,426, 122,426, 95,410, 74,375,
            49,365, 41,345, 0,344, 0,309, 33,302, 63,287, 60,270, 88,261,
            112,233, 115,201, 184,201, 190,195, 188,177, 182,177, 181,182,
            127,183, 113,157, 83,140, 68,123, 55,97, 23,88, 34,64, 68,48,
            75,33, 107,25, 119,10
    };
    private static final float[] ISLET = {
            233,129, 244,115, 269,106, 291,104, 314,108, 328,119,
            354,128, 356,139, 337,148, 311,153, 277,152, 249,143
    };
    static final int WAKE_COUNT = 22;
    final float[] wakeX = new float[WAKE_COUNT], wakeY = new float[WAKE_COUNT];
    final float[] wakeHeading = new float[WAKE_COUNT], wakeAge = new float[WAKE_COUNT];
    float x, y, vx, vy, heading;
    int delivered;
    float deliveryAge;
    private int nextWake;
    private float wakeDistance;

    SpriteMovementState() {
        reset();
    }

    void reset() {
        x = START_X;
        y = START_Y;
        vx = vy = wakeDistance = 0;
        heading = -48;
        delivered = nextWake = 0;
        deliveryAge = 1;
        java.util.Arrays.fill(wakeAge, 1);
    }

    void update(MovementInput input, float dt) {
        float dx = input.horizontal(), dy = -input.vertical();
        float length = (float) Math.sqrt(dx * dx + dy * dy);
        if (length > 1) {
            dx /= length;
            dy /= length;
        }
        float remaining = Math.min(dt, .12f);
        while (remaining > 0 && delivered < ROUTE.length) {
            float step = Math.min(remaining, 1f / 90);
            move(dx * SPEED, dy * SPEED, step);
            remaining -= step;
        }
    }

    private void move(float targetX, float targetY, float dt) {
        float dx = targetX - vx, dy = targetY - vy;
        float difference = (float) Math.sqrt(dx * dx + dy * dy);
        if (difference > 0) {
            float change = Math.min(1, 280 * dt / difference);
            vx += dx * change;
            vy += dy * change;
        }
        float oldX = x, oldY = y;
        if (water(x + vx * dt, y)) x += vx * dt;
        else vx = 0;
        if (water(x, y + vy * dt)) y += vy * dt;
        else vy = 0;
        if (vx * vx + vy * vy > 4) {
            float target = (float) Math.toDegrees(Math.atan2(vx, -vy));
            float turn = (target - heading + 540) % 360 - 180;
            heading = (heading + Math.max(-360 * dt, Math.min(360 * dt, turn)) + 360) % 360;
        }
        for (int i = 0; i < WAKE_COUNT; i++) wakeAge[i] = Math.min(1, wakeAge[i] + dt);
        wakeDistance += (float) Math.hypot(x - oldX, y - oldY);
        if (wakeDistance >= 5) {
            double angle = Math.toRadians(heading);
            wakeX[nextWake] = x - 8 * (float) Math.sin(angle);
            wakeY[nextWake] = y + 8 * (float) Math.cos(angle);
            wakeHeading[nextWake] = heading;
            wakeAge[nextWake] = 0;
            nextWake = (nextWake + 1) % WAKE_COUNT;
            wakeDistance -= 5;
        }
        deliveryAge = Math.min(1, deliveryAge + dt);
        Dock dock = ROUTE[delivered];
        float distanceX = x - dock.x, distanceY = y - dock.y;
        if (distanceX * distanceX + distanceY * distanceY <= 13 * 13) {
            delivered++;
            deliveryAge = 0;
        }
    }

    static boolean water(float x, float y) {
        return insideWithClearance(BAY, x, y, true) && insideWithClearance(ISLET, x, y, false);
    }

    private static boolean insideWithClearance(float[] polygon, float x, float y, boolean wantInside) {
        boolean inside = false;
        for (int i = 0, j = polygon.length - 2; i < polygon.length; j = i, i += 2) {
            float ax = polygon[j], ay = polygon[j + 1], bx = polygon[i], by = polygon[i + 1];
            float dx = bx - ax, dy = by - ay;
            float t = Math.max(0, Math.min(1, ((x - ax) * dx + (y - ay) * dy) / (dx * dx + dy * dy)));
            float ex = x - ax - t * dx, ey = y - ay - t * dy;
            if (ex * ex + ey * ey < 10 * 10) return false;
            if ((ay > y) != (by > y) && x < ax + (y - ay) * dx / dy) inside = !inside;
        }
        return inside == wantInside;
    }

}
