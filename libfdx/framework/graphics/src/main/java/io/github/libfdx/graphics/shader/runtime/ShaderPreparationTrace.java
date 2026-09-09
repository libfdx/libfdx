package io.github.libfdx.graphics.shader.runtime;

import java.util.function.Consumer;
import java.util.function.LongSupplier;

/**
 * Provider instrumentation for one operation. Record transitions where they happen, not by
 * polling. Short synchronized updates never hold a lock around native work. Scoped execution
 * attributes cache completions to their initiating operation across workers.
 */
public final class ShaderPreparationTrace {
    private static final ThreadLocal<ShaderPreparationTrace> CURRENT = new ThreadLocal<>();
    private final LongSupplier clock;
    private final long[] phases = new long[ShaderPreparationPhase.values().length];
    private final long[][] caches = new long[ShaderCacheLayer.values().length][13];
    private ShaderPreparationPhase phase = ShaderPreparationPhase.QUEUED;
    private long since;

    public ShaderPreparationTrace() { this(System::nanoTime); }
    ShaderPreparationTrace(LongSupplier clock) { this.clock = clock; since = clock.getAsLong(); }
    public synchronized void enter(ShaderPreparationPhase next) {
        if (phase == ShaderPreparationPhase.COMPLETE || phase == next) return;
        long now = clock.getAsLong();
        phases[phase.ordinal()] += Math.max(0, now - since);
        phase = next;
        since = now;
    }
    public synchronized ShaderPreparationPhase phase() { return phase; }
    public static ShaderPreparationTrace current() { return CURRENT.get(); }
    public ShaderPreparationTrace attach() {
        ShaderPreparationTrace previous = CURRENT.get();
        CURRENT.set(this);
        return previous;
    }
    public static void restore(ShaderPreparationTrace previous) {
        if (previous == null) CURRENT.remove(); else CURRENT.set(previous);
    }
    public Runnable wrap(Runnable work) {
        return () -> {
            ShaderPreparationTrace previous = attach();
            try { work.run(); } finally { restore(previous); }
        };
    }
    public Consumer<Runnable> executor(Consumer<Runnable> execute) {
        return work -> execute.accept(wrap(work));
    }
    synchronized void count(ShaderCacheLayer layer, int metric) { caches[layer.ordinal()][metric]++; }
    synchronized void cacheElapsed(ShaderCacheLayer layer, boolean write, long started) {
        caches[layer.ordinal()][write ? 12 : 11] += Math.max(0, System.nanoTime() - started);
    }
    synchronized ShaderPreparationTimings snapshot(long queue, long preparation, long firstDraw,
            long readyToDraw, long update) {
        long[] phaseCopy = phases.clone();
        if (phase != ShaderPreparationPhase.COMPLETE)
            phaseCopy[phase.ordinal()] += Math.max(0, clock.getAsLong() - since);
        long[][] cacheCopy = new long[caches.length][];
        for (int i = 0; i < caches.length; i++) cacheCopy[i] = caches[i].clone();
        return new ShaderPreparationTimings(queue, preparation, firstDraw, readyToDraw, update, phaseCopy, cacheCopy);
    }
}
