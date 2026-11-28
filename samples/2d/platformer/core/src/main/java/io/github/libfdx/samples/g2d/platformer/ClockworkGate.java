package io.github.libfdx.samples.g2d.platformer;

import io.github.libfdx.core.FdxException;

/** One map-authored puzzle. Coordinates are simulation units, with Y measured from the floor. */
public final class ClockworkGate {
    public static final float HEIGHT = 82 / PlatformerLevel.PIXELS_PER_UNIT;
    public static final float HALF_WIDTH = 6 / PlatformerLevel.PIXELS_PER_UNIT;
    private static final int[] CIRCUITS = {0b011, 0b110, 0b100};
    private final float[] dialX = new float[3], dialY = new float[3];
    private int installed, setting, initialLamps, sequence, progress, lamps, lastDial;
    private String clue;
    private float x, y, opening, mistakeTime;
    private boolean solved;

    void dial(int number, float px, float py) {
        if (number < 1 || number > 3 || (installed & (1 << (number - 1))) != 0) {
            throw new FdxException("Expected three uniquely numbered puzzle dials");
        }
        installed |= 1 << (number - 1);
        dialX[number - 1] = px;
        dialY[number - 1] = py;
    }

    void mechanism(boolean bells, int value, String hint, float px, float py) {
        if (clue != null || hint.isEmpty()) throw new FdxException("Expected one gate with a clue");
        if (bells) {
            int a = value / 100, b = value / 10 % 10, c = value % 10;
            if (a < 1 || a > 3 || b < 1 || b > 3 || c < 1 || c > 3 || a == b || a == c || b == c) {
                throw new FdxException("Bell code must be a permutation of 1, 2 and 3");
            }
            sequence = value;
        } else {
            if (value < 0 || value >= 7) throw new FdxException("Expected an unsolved three-lamp circuit");
            initialLamps = value;
        }
        clue = hint;
        x = px;
        y = py;
        reset();
    }

    void validate(float destinationX) {
        if (clue == null || installed != 7 || x >= destinationX) throw new FdxException("Incomplete puzzle chamber");
        for (float dial : dialX) {
            if (dial >= x) throw new FdxException("Puzzle dial is behind its gate");
        }
    }

    void reset() {
        solved = false;
        opening = mistakeTime = 0;
        lamps = initialLamps;
        progress = setting = 0;
    }

    void update(float dt) {
        if (solved) opening = Math.min(1, opening + dt * 1.6f);
        mistakeTime = Math.max(0, mistakeTime - dt);
    }

    int nearby(PlatformerGame.Sprite player) {
        if (solved) return -1;
        for (int i = 0; i < 3; i++) {
            if (Math.abs(player.x() - dialX[i]) <= 20 / PlatformerLevel.PIXELS_PER_UNIT
                    && Math.abs(player.y() - player.halfHeight() - dialY[i]) < 6 / PlatformerLevel.PIXELS_PER_UNIT) return i;
        }
        return -1;
    }

    void turn(int index) {
        if (solved) return;
        lastDial = index;
        mistakeTime = 0;
        if (bells()) {
            int digit = index + 1;
            int expected = sequence / (progress == 0 ? 100 : progress == 1 ? 10 : 1) % 10;
            if (digit != expected) {
                mistakeTime = 1.1f;
                progress = setting = 0;
                return;
            }
            setting = setting * 10 + digit;
            progress++;
            solved = progress == 3;
        } else {
            lamps ^= CIRCUITS[index];
            solved = lamps == 7;
        }
    }

    public boolean inChamber(float playerX) {
        float first = Math.min(dialX[0], Math.min(dialX[1], dialX[2]));
        return playerX >= first - .67f && playerX <= x + .42f;
    }

    public boolean bells() { return sequence != 0; }

    public String clue() { return clue; }

    public float x() { return x; }

    public float y() { return y; }

    public float dialX(int i) { return dialX[i]; }

    public float dialY(int i) { return dialY[i]; }

    public boolean lit(int i) { return (lamps & (1 << i)) != 0; }

    public int lamps() { return lamps; }

    public int rung() { return setting; }

    public int progress() { return progress; }

    public int lastDial() { return lastDial; }

    public boolean mistake() { return mistakeTime > 0; }

    public boolean solved() { return solved; }

    public float opening() { return opening; }

}
