package io.github.libfdx.graphics.shader.runtime;

/** Reusable per-batch logical draw counters; sprites or model renderables, not native calls.
 * Application-thread owned. Counts reset at the next preparation publication boundary.
 * Reading after rendering gives that frame's counts, including all passes/begin-end pairs. */
public final class ShaderSkippedDraws {
    private long frame = Long.MIN_VALUE;
    private int pending, failed, unsupported, cancelled;

    public void beginFrame(long index) {
        if (frame == index) return;
        frame = index;
        pending = failed = unsupported = cancelled = 0;
    }

    public void record(ShaderPreparationState state, int logicalDraws) {
        if (logicalDraws < 0) throw new IllegalArgumentException("Negative draw count");
        switch (state) {
            case QUEUED, PREPARING -> pending += logicalDraws;
            case FAILED -> failed += logicalDraws;
            case UNSUPPORTED -> unsupported += logicalDraws;
            case CANCELLED -> cancelled += logicalDraws;
            case READY -> throw new IllegalArgumentException("A ready draw was not skipped");
        }
    }

    public int total() { return pending + failed + unsupported + cancelled; }
    public int pending() { return pending; }
    public int failed() { return failed; }
    public int unsupported() { return unsupported; }
    public int cancelled() { return cancelled; }
}
