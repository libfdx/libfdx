package io.github.libfdx.ui;

import io.github.libfdx.input.PointerType;

/** Counts completed primary clicks or taps on the same target using UI-space coordinates.
 * Keep one instance per interaction owner on the UI thread and clear it after a drag or cancellation. */
public final class UiTapSequence {
    static final float SLOP = 6;
    private Object target;
    private PointerType type;
    private int pointerId, count;
    private long time;
    private float x, y;

    /** Returns whether two points are within the kit's tap tolerance in logical UI units. */
    public static boolean withinSlop(float x, float y, float otherX, float otherY) {
        float dx = otherX - x, dy = otherY - y;
        return dx * dx + dy * dy <= SLOP * SLOP;
    }

    /** Records a completed tap. The target is compared by identity and retained until cleared or replaced. */
    public int complete(Object target, PointerType type, int pointerId, long nanos, float x, float y) {
        long elapsed = nanos - time;
        boolean repeat = count > 0 && this.target == target && this.type == type && this.pointerId == pointerId
                && elapsed >= 0 && elapsed <= 500_000_000L && withinSlop(this.x, this.y, x, y);
        count = repeat ? count + 1 : 1;
        this.target = target;
        this.type = type;
        this.pointerId = pointerId;
        time = nanos;
        this.x = x;
        this.y = y;
        return count;
    }

    /** Forgets previous taps and releases the target. */
    public void clear() {
        target = null;
        count = 0;
    }

    void forget(Object removed) {
        if (target == removed) clear();
    }

}
