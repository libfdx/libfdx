package io.github.libfdx.graphics.shader.runtime;

import io.github.libfdx.core.Disposable;
import io.github.libfdx.core.FdxException;

/**
 * One application's retained interest in a shared preparation entry. Cache this handle when
 * configuration changes; do not request another handle every frame. All access is confined to
 * the preparation service's application thread. Disposing one handle never releases another
 * consumer's interest.
 */
public final class PreparedShaderPass implements Disposable {
    final ShaderPreparation owner;
    final ShaderPreparation.Entry entry;
    private boolean disposed;

    PreparedShaderPass(ShaderPreparation owner, ShaderPreparation.Entry entry) {
        this.owner = owner;
        this.entry = entry;
        entry.references++;
    }

    public ShaderPreparationState state() {
        owner.requireThread();
        return disposed || owner.isDisposed() ? ShaderPreparationState.CANCELLED : entry.state;
    }

    public ShaderPreparationPhase phase() {
        owner.requireThread();
        return disposed || owner.isDisposed() ? ShaderPreparationPhase.COMPLETE : entry.phase();
    }

    /** Allocates an immutable diagnostic snapshot, including cache writes completed since readiness. */
    public ShaderPreparationTimings timings() { owner.requireThread(); return entry.timings(); }

    public ShaderRequest request() { return entry.key.request; }

    /** Failure diagnostics, or null when no failure was recorded. */
    public Throwable failure() { owner.requireThread(); return entry.failure; }

    /** Borrowed pass, or null while not ready. Never blocks or triggers preparation. */
    public ResolvedShaderPass readyPass() {
        return state() == ShaderPreparationState.READY ? entry.result.pass() : null;
    }

    /** Explicit gameplay demand for developer captures; loading/polling does not count as a draw.
     * Reuse an immutable origin created at configuration changes. Has no allocation when captures
     * are inactive or this requirement is already known. logicalDraws counts sprites/renderables,
     * and skipped says whether those draws were omitted because preparation was unavailable. */
    public void recordDraws(ShaderPreparationOrigin origin, int logicalDraws, boolean skipped) {
        origin = origin != null ? origin : ShaderPreparationOrigin.UNKNOWN;
        recordDraws(origin, origin.content(), origin.material(), logicalDraws, skipped);
    }

    /** Records content labels for a shared definition without allocating a new origin each draw.
     * Captures copy previously unseen origins within their bounds. Existing observations and
     * calls without active captures allocate no origin objects. Null labels mean unknown. */
    public void recordDraws(ShaderPreparationOrigin definition, String content, String material,
            int logicalDraws, boolean skipped) {
        owner.requireThread();
        if (disposed) throw new FdxException("Prepared shader handle is disposed");
        if (logicalDraws < 0) throw new IllegalArgumentException("Negative logical draw count");
        owner.recordDraws(entry, definition != null ? definition : ShaderPreparationOrigin.UNKNOWN,
                content != null ? content : "", material != null ? material : "", logicalDraws, skipped);
    }

    /** Adds another independent retained interest. */
    public PreparedShaderPass retain() {
        owner.requireThread();
        if (disposed || owner.isDisposed()) throw new FdxException("Prepared shader handle is disposed");
        return new PreparedShaderPass(owner, entry);
    }

    @Override public void dispose() {
        owner.requireThread();
        if (disposed) return;
        disposed = true;
        owner.release(entry);
    }

    @Override public boolean isDisposed() { return disposed; }
}
