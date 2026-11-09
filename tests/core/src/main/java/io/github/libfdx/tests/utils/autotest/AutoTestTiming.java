package io.github.libfdx.tests.utils.autotest;

/** Per-scene clock: startup and stabilization never consume the observation interval. */
public final class AutoTestTiming {
    private final float duration, spikeThreshold, loadTimeout;
    private final int requiredStableFrames;
    private int ignoredFrames, stableFrames;
    private float loadingSeconds, runningSeconds;
    private boolean loaded;

    public AutoTestTiming(float duration, int requiredStableFrames, float spikeThreshold, float loadTimeout) {
        if (!Float.isFinite(duration) || duration <= 0 || requiredStableFrames < 1
                || !Float.isFinite(spikeThreshold) || spikeThreshold <= 0
                || !Float.isFinite(loadTimeout) || loadTimeout <= 0) {
            throw new IllegalArgumentException("Automatic test timing must be finite and positive");
        }
        this.duration = duration;
        this.requiredStableFrames = requiredStableFrames;
        this.spikeThreshold = spikeThreshold;
        this.loadTimeout = loadTimeout;
        restart();
    }

    void restart() {
        // The switching frame has the preceding scene's delta; the next includes create().
        ignoredFrames = 2;
        stableFrames = 0;
        loadingSeconds = runningSeconds = 0;
        loaded = false;
    }

    public boolean update(float deltaSeconds) {
        if (ignoredFrames > 0) {
            ignoredFrames--;
            return false;
        }
        if (!Float.isFinite(deltaSeconds) || deltaSeconds < 0) {
            throw new IllegalArgumentException("Frame duration must be finite and nonnegative");
        }
        if (!loaded) {
            loadingSeconds += deltaSeconds;
            stableFrames = deltaSeconds < spikeThreshold ? stableFrames + 1 : 0;
            loaded = stableFrames >= requiredStableFrames || loadingSeconds >= loadTimeout;
            return false;
        }
        runningSeconds += deltaSeconds;
        return runningSeconds >= duration;
    }

    public boolean update(float deltaSeconds, boolean ready) {
        if (!ready) {
            restart();
            return false;
        }
        return update(deltaSeconds);
    }

    public boolean loaded() { return loaded; }

}
