package io.github.libfdx.application;

/**
 * Application-owned fixed simulation clock. Call advance once per rendered frame,
 * execute its returned number of steps using stepSeconds(), and interpolate visuals
 * with alpha(). It never changes backend render timing or invokes callbacks.
 * All operations are application-thread confined and allocate no per-frame storage.
 */
public final class FixedStepClock {
    private final double stepSeconds;
    private final int maxSteps;
    private double accumulator;
    private double droppedSeconds;
    private boolean paused;

    /** Configures a finite positive timestep and a bounded catch-up limit of 1–10,000 steps per frame. */
    public FixedStepClock(double stepSeconds, int maxSteps) {
        if (!Double.isFinite(stepSeconds) || stepSeconds <= 0 || maxSteps < 1 || maxSteps > 10000
                || !Double.isFinite(stepSeconds * (maxSteps + 1.0))) throw new IllegalArgumentException("Invalid fixed-step limits");
        this.stepSeconds = stepSeconds; this.maxSteps = maxSteps;
    }
    /**
     * Adds nonnegative finite elapsed seconds and consumes the returned simulation steps.
     * At most maxSteps * stepSeconds of each frame is admitted; excess time is deliberately
     * dropped to prevent a pause/stall from causing unbounded catch-up. Paused time is
     * discarded. droppedSeconds() reports the most recent call's discarded seconds.
     */
    public int advance(double deltaSeconds) {
        if (!Double.isFinite(deltaSeconds) || deltaSeconds < 0) throw new IllegalArgumentException("Delta must be finite and nonnegative");
        if (paused) { droppedSeconds = deltaSeconds; return 0; }
        double admitted = Math.min(deltaSeconds, stepSeconds * maxSteps);
        droppedSeconds = deltaSeconds - admitted;
        accumulator += admitted;
        int steps = Math.min(maxSteps, (int) Math.floor(accumulator / stepSeconds + 1e-10));
        accumulator = Math.max(0, accumulator - steps * stepSeconds);
        return steps;
    }
    /** Fixed simulation delta in seconds. */
    public double stepSeconds() { return stepSeconds; }
    /** Visual interpolation factor in [0,1); simulation never uses this as its timestep. */
    public double alpha() { return Math.min(Math.nextDown(1.0), accumulator / stepSeconds); }
    /** Time discarded by the most recent advance call. */
    public double droppedSeconds() { return droppedSeconds; }
    /** Clears the interpolation remainder and pauses; repeated calls are safe. */
    public void pause() { paused = true; reset(); }
    /** Resumes without accumulating wall-clock time spent paused. */
    public void resume() { paused = false; reset(); }
    /** Clears remainder and last dropped-time value, retaining pause state. */
    public void reset() { accumulator = 0; droppedSeconds = 0; }
    /** Reports explicit pause state. */
    public boolean isPaused() { return paused; }
}
