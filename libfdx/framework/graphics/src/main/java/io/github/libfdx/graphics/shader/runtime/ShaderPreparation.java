package io.github.libfdx.graphics.shader.runtime;

import io.github.libfdx.core.Disposable;
import io.github.libfdx.core.FdxException;
import io.github.libfdx.core.FdxFuture;
import io.github.libfdx.graphics.GraphicsContext;
import io.github.libfdx.graphics.GraphicsDevice;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.LongSupplier;

/**
 * Application-owned preparation queue and resource residency service for one native domain.
 *
 * <p>All public access is confined to the constructing application thread. Call update() before
 * opening render passes, including while loading and draining. Requests only collect work;
 * providers perform isolated asynchronous preparation. No synchronous resolve fallback exists.
 * Capture handles at content/configuration changes and reuse readyPass() during drawing.</p>
 *
 * <p>dispose() starts cancellation without waiting. Continue update() until disposeAsync()'s
 * future completes before destroying the graphics device. Providers must defer disposal of
 * resources referenced by submitted GPU work. Scopes and retained handles pin ready entries.</p>
 */
public final class ShaderPreparation implements Disposable {
    private final Thread applicationThread = Thread.currentThread();
    private final GraphicsDevice device;
    private final Object domain;
    private final ShaderPreparationOptions options;
    private final LongSupplier updateClock;
    private final Map<Key, Entry> lookup = new HashMap<>();
    private final List<Entry> entries = new ArrayList<>();
    private final List<ShaderPreparationScope> scopes = new ArrayList<>();
    private final List<ShaderPreloadCapture> captures = new ArrayList<>();
    private final LinkedHashMap<Key, Boolean> preloadHistory = new LinkedHashMap<>();
    private long forgottenPreloadDeclarations;
    private final ArrayDeque<Runnable> callbacks = new ArrayDeque<>();
    private final FdxFuture<Void> drained = newFuture();
    private int active, submissionTurn, pollCursor;
    private long clock, frameIndex, entrySequence;
    private boolean disposed, updating;

    public ShaderPreparation(GraphicsContext graphics) {
        this(graphics, ShaderPreparationOptions.DEFAULT);
    }

    public ShaderPreparation(GraphicsContext graphics, ShaderPreparationOptions options) {
        this(Objects.requireNonNull(graphics, "graphics").device(), options);
    }

    public ShaderPreparation(GraphicsDevice device, ShaderPreparationOptions options) {
        this(device, options, System::nanoTime);
    }

    ShaderPreparation(GraphicsDevice device, ShaderPreparationOptions options, LongSupplier updateClock) {
        this.device = Objects.requireNonNull(device, "device");
        domain = Objects.requireNonNull(device.resourceDomain(), "resourceDomain");
        this.options = Objects.requireNonNull(options, "options");
        this.updateClock = Objects.requireNonNull(updateClock, "updateClock");
    }

    public GraphicsDevice device() { return device; }
    /** Monotonic publication boundary. Call update once per application frame, before passes. */
    public long frameIndex() { requireThread(); return frameIndex; }
    public ShaderPreparationCapabilities capabilities() { return device.shaderPreparationCapabilities(); }

    public ShaderPreloadCapture captureRuntime(String label) { return captureRuntime(label, 4096); }

    /** Starts bounded explicit draw observation. At most 16 captures can be active per service.
     * Each capture retains at most 16 origins per requirement and 65,536 origins in total;
     * excess demands appear in the export's incompleteness diagnostics. */
    public ShaderPreloadCapture captureRuntime(String label, int maxRequirements) {
        requireOpen();
        if (maxRequirements < 1 || maxRequirements > 65536 || captures.size() >= 16) {
            throw new FdxException("Invalid shader capture limit or too many active captures");
        }
        ShaderPreloadCapture capture = new ShaderPreloadCapture(this, label, maxRequirements);
        captures.add(capture);
        return capture;
    }

    void removeCapture(ShaderPreloadCapture capture) { captures.remove(capture); }
    void recordDraws(Entry entry, ShaderPreparationOrigin origin, String content, String material, int draws, boolean skipped) {
        if (draws == 0) return;
        for (int i = 0; i < captures.size(); i++) captures.get(i).observe(entry, origin, content, material, draws, skipped);
    }
    /** The bounded residency-history catalog keeps 4096 evicted preload declarations. A nonzero
     * value means older misses may be classified as not preloaded instead of residency lost. */
    public long forgottenPreloadDeclarations() { requireThread(); return forgottenPreloadDeclarations; }

    public ShaderPreparationScope createScope(String label) {
        requireOpen();
        ShaderPreparationScope scope = new ShaderPreparationScope(this, Objects.requireNonNull(label, "label"));
        scopes.add(scope);
        return scope;
    }

    /** Sealed scopes complete through update(), even when empty or already prepared. */
    public FdxFuture<ShaderPreparationReport> prepareAsync(ShaderPreparationScope scope) {
        requireOpen();
        if (scope == null || scope.owner != this || !scope.sealed || scope.disposed) {
            throw new FdxException("Expected a live sealed scope from this preparation service");
        }
        scope.submitted = true;
        return scope.completion;
    }

    /** Returns a newly retained consumer handle; structurally equal requests share native work. */
    public PreparedShaderPass request(ShaderProvider provider, ShaderRequest request) {
        return request(provider, request, true);
    }

    PreparedShaderPass request(ShaderProvider provider, ShaderRequest request, boolean runtime) {
        requireOpen();
        Objects.requireNonNull(provider, "provider");
        Objects.requireNonNull(request, "request");
        if (request.renderPass() == null) throw new FdxException("Preparation requires a render-target layout");
        GraphicsDevice providerDevice = provider.preparationDevice();
        if (providerDevice != null && providerDevice.resourceDomain() != domain) {
            throw new FdxException("Shader provider belongs to another graphics resource domain");
        }
        Key key = new Key(provider, provider.revision(), request);
        Entry entry = lookup.get(key);
        if (entry != null && !runtime && entry.loadingRejected) {
            lookup.remove(key);
            entry = null;
        }
        if (entry == null) {
            entry = new Entry(key, ++entrySequence);
            entry.residencyLost = preloadHistory.containsKey(key);
            if (providerDevice == null
                    || providerDevice.shaderPreparationCapabilities().cpuExecution()
                        == ShaderPreparationCapabilities.Execution.UNAVAILABLE
                    || providerDevice.shaderPreparationCapabilities().nativeExecution()
                        == ShaderPreparationCapabilities.Execution.UNAVAILABLE
                    || !provider.supports(request)) {
                entry.state = ShaderPreparationState.UNSUPPORTED;
                entry.failure = new FdxException("Shader provider cannot asynchronously prepare this request");
            } else if (runtime && !providerDevice.shaderPreparationCapabilities().runtimeNonblocking()) {
                entry.state = ShaderPreparationState.UNSUPPORTED;
                entry.failure = new FdxException("Selected provider supports loading-only preparation");
                entry.loadingRejected = true;
            }
            lookup.put(key, entry);
            entries.add(entry);
            if (entry.state.terminal()) entry.finishedNanos = entry.queuedNanos;
        }
        entry.preloadDeclared |= !runtime;
        // A runtime consumer can use an already-prepared loading-only resource without compiling.
        entry.runtimeDemand |= runtime;
        entry.lastUse = ++clock;
        return new PreparedShaderPass(this, entry);
    }

    /** Explicitly retries a failed/unsupported entry. Existing consumers retain their old outcome. */
    public PreparedShaderPass retry(PreparedShaderPass previous) {
        requireOpen();
        if (previous == null || previous.owner != this || previous.isDisposed()) {
            throw new FdxException("Expected a live handle from this service");
        }
        Entry entry = previous.entry;
        if (entry.state != ShaderPreparationState.FAILED && entry.state != ShaderPreparationState.UNSUPPORTED) {
            throw new FdxException("Only failed or unsupported preparation can be retried");
        }
        lookup.remove(entry.key, entry);
        return request(entry.key.provider, entry.key.request, entry.runtimeDemand);
    }

    /**
     * Publishes completed work, submits bounded work, and delivers scope callbacks.
     * Cleanup and callback failures are propagated after processing the other completed work
     * within the budget. Retained READY revisions remain usable until their consumers release
     * them; renderers select compatible old passes explicitly during hot reload.
     */
    public void update() {
        update(false, Long.MAX_VALUE);
    }

    /** Like {@link #update()}, with a cooperative elapsed-time budget in nanoseconds. Checks the
     * budget between provider operations, submissions and callbacks; an individual operation cannot
     * be interrupted. Zero performs no work; negative budgets fail. Polling rotates between updates
     * so a slow pending operation cannot starve other pending operations. */
    public void update(long maxNanos) { update(false, maxNanos); }

    /**
     * Explicit loading-screen update. Additionally allows providers with owner-thread compilation
     * to start and advance preload work. Such a native call may block; use update() during gameplay instead.
     * Runtime requests on these providers remain unsupported unless already preloaded.
     */
    public void updateLoading() {
        update(true, Long.MAX_VALUE);
    }

    /** Loading-screen variant of {@link #update(long)}. A provider's single native compiler call
     * can exceed the budget; use {@link #update(long)} during gameplay on nonblocking providers. */
    public void updateLoading(long maxNanos) { update(true, maxNanos); }

    private boolean withinBudget(long start, long maxNanos) {
        return maxNanos == Long.MAX_VALUE || updateClock.getAsLong() - start < maxNanos;
    }

    private void update(boolean loading, long maxNanos) {
        requireThread();
        if (maxNanos < 0) throw new FdxException("Shader update budget cannot be negative");
        if (updating) throw new FdxException("ShaderPreparation.update cannot be called recursively");
        if (maxNanos == 0) return;
        long start = updateClock.getAsLong();
        updating = true;
        frameIndex++;
        Throwable callbackFailure = null;
        try {
            if (!disposed && device.resourceDomain() != domain) {
                try { dispose(); }
                catch (Throwable failure) { callbackFailure = combine(callbackFailure, failure); }
            }
            int remaining = options.maxPublicationsPerUpdate();
            int entryCount = entries.size();
            for (int i = 0; i < entryCount && withinBudget(start, maxNanos); i++) {
                if (pollCursor >= entries.size()) pollCursor = 0;
                Entry entry = entries.get(pollCursor++);
                if (!disposed && entry.state != ShaderPreparationState.CANCELLED
                        && entry.state != ShaderPreparationState.READY
                        && entry.key.provider.revision() != entry.key.revision) {
                    try { invalidate(entry); }
                    catch (Throwable failure) { callbackFailure = combine(callbackFailure, failure); }
                }
                try {
                    if (entry.operation != null && remaining > 0) {
                        ShaderPreparationTrace previous = entry.trace != null ? entry.trace.attach() : ShaderPreparationTrace.current();
                        try {
                            if (loading && !disposed && entry.state == ShaderPreparationState.PREPARING)
                                entry.operation.advanceLoading();
                            if (entry.operation.isDone()) {
                                remaining--;
                                finish(entry);
                            }
                        } finally { ShaderPreparationTrace.restore(previous); }
                    }
                    if (disposed && entry.result != null) releaseResult(entry);
                } catch (Throwable failure) {
                    callbackFailure = combine(callbackFailure, failure);
                }
            }
            if (!disposed) {
                int submissions = options.maxInFlight();
                while (active < options.maxInFlight() && submissions-- > 0 && withinBudget(start, maxNanos)) {
                    // Every fourth opportunity prefers preload, so visible work cannot starve it.
                    boolean runtimeFirst = (++submissionTurn & 3) != 0;
                    Entry entry = nextQueued(runtimeFirst, loading);
                    if (entry == null) entry = nextQueued(!runtimeFirst, loading);
                    if (entry == null) break;
                    try {
                        entry.startedNanos = System.nanoTime();
                        entry.operation = Objects.requireNonNull(
                                entry.key.provider.beginPreparation(entry.key.request), "preparation operation");
                        entry.trace = entry.operation.trace();
                        entry.state = ShaderPreparationState.PREPARING;
                        active++;
                    } catch (Throwable failure) {
                        entry.failure = failure;
                        entry.state = ShaderPreparationState.FAILED;
                        entry.finishedNanos = System.nanoTime();
                    }
                }
            }
            int scopeCount = scopes.size();
            for (int i = 0; i < scopeCount && withinBudget(start, maxNanos); i++) {
                ShaderPreparationScope scope = scopes.get(i);
                if (scope.submitted && !scope.completion.isDone() && scope.settled()) {
                    try { scope.complete(); }
                    catch (Throwable failure) { callbackFailure = combine(callbackFailure, failure); }
                }
            }
            for (int i = 0; i < captures.size(); i++) captures.get(i).publish();
            try { trimIdle(); }
            catch (Throwable failure) { callbackFailure = combine(callbackFailure, failure); }
            if (disposed && active == 0 && !drained.isDone()) {
                try { drained.complete(null); }
                catch (Throwable failure) { callbackFailure = combine(callbackFailure, failure); }
            }
            // Bound listeners too, including late registrations and listeners adding listeners.
            for (int i = 0; i < options.maxPublicationsPerUpdate() && withinBudget(start, maxNanos); i++) {
                Runnable callback;
                synchronized (callbacks) { callback = callbacks.pollFirst(); }
                if (callback == null) break;
                try { callback.run(); }
                catch (Throwable failure) { callbackFailure = combine(callbackFailure, failure); }
            }
        } finally {
            updating = false;
        }
        if (callbackFailure instanceof Error error) throw error;
        if (callbackFailure instanceof RuntimeException error) throw error;
        if (callbackFailure != null) throw new FdxException("Shader preparation callback failed", callbackFailure);
    }

    private Entry nextQueued(boolean runtime, boolean loading) {
        for (int i = 0; i < entries.size(); i++) {
            Entry entry = entries.get(i);
            if (entry.state == ShaderPreparationState.QUEUED && entry.references > 0
                    && entry.runtimeDemand == runtime
                    && (loading || entry.key.provider.preparationDevice()
                        .shaderPreparationCapabilities().runtimeNonblocking())) return entry;
        }
        return null;
    }

    private void finish(Entry entry) {
        ShaderPreparationOperation operation = entry.operation;
        ShaderPreparedResult result = null;
        try {
            if (entry.trace != null) entry.trace.enter(ShaderPreparationPhase.PUBLICATION);
            result = Objects.requireNonNull(operation.finish(), "prepared result");
            if (disposed || entry.state == ShaderPreparationState.CANCELLED
                    || entry.key.provider.revision() != entry.key.revision) {
                entry.state = ShaderPreparationState.CANCELLED;
            } else {
                ResolvedShaderPass pass = result.pass();
                if (!pass.passId().equals(entry.key.request.passId())
                        || pass.providerRevision() != entry.key.revision
                        || !pass.pipeline().targetLayout().equals(entry.key.request.renderPass().targetLayout())) {
                    throw new FdxException("Prepared result does not match its requested pass, revision or target");
                }
                entry.result = result;
                result = null;
                entry.state = ShaderPreparationState.READY;
                pass.observeFirstDraw(() -> recordFirstDraw(entry));
            }
        } catch (Throwable failure) {
            if (entry.state != ShaderPreparationState.CANCELLED && !disposed) {
                entry.failure = failure;
                entry.state = ShaderPreparationState.FAILED;
            }
        } finally {
            entry.finishedNanos = System.nanoTime();
            if (entry.trace != null) entry.trace.enter(ShaderPreparationPhase.COMPLETE);
            try {
                if (result != null) result.dispose();
            } finally {
                entry.operation = null;
                active--;
                operation.dispose();
            }
        }
    }

    private void recordFirstDraw(Entry entry) {
        requireThread();
        if (disposed || entry.state != ShaderPreparationState.READY || entry.firstDrawUpdate >= 0) return;
        entry.firstDrawNanos = System.nanoTime();
        entry.firstDrawUpdate = frameIndex;
    }

    private void invalidate(Entry entry) {
        if (entry.state == ShaderPreparationState.CANCELLED) return;
        entry.state = ShaderPreparationState.CANCELLED;
        if (entry.operation == null && entry.finishedNanos == 0) entry.finishedNanos = System.nanoTime();
        lookup.remove(entry.key, entry);
        if (entry.operation != null) entry.operation.cancel();
        if (entry.result != null) releaseResult(entry);
    }

    private void releaseResult(Entry entry) {
        ShaderPreparedResult result = entry.result;
        entry.result = null;
        result.dispose();
    }

    void release(Entry entry) {
        if (--entry.references < 0) throw new IllegalStateException("Unbalanced shader preparation lease");
        entry.lastUse = ++clock;
        if (entry.references == 0 && entry.state == ShaderPreparationState.QUEUED) {
            entry.state = ShaderPreparationState.CANCELLED;
            entry.finishedNanos = System.nanoTime();
            lookup.remove(entry.key, entry);
        }
        // Running native work is retained and may serve another arriving consumer. It never
        // receives a fresh job solely because the previous consumer released its handle.
    }

    private void trimIdle() {
        int idle = 0;
        for (int i = 0; i < entries.size(); i++) {
            Entry entry = entries.get(i);
            if (entry.references == 0 && entry.operation == null) idle++;
        }
        int limit = disposed ? 0 : options.idleCapacity();
        while (idle > limit) {
            Entry oldest = null;
            for (int i = 0; i < entries.size(); i++) {
                Entry candidate = entries.get(i);
                if (candidate.references == 0 && candidate.operation == null
                        && (oldest == null || candidate.lastUse < oldest.lastUse)) oldest = candidate;
            }
            if (oldest == null) break;
            if (oldest.preloadDeclared) {
                preloadHistory.put(oldest.key, Boolean.TRUE);
                if (preloadHistory.size() > 4096) {
                    preloadHistory.remove(preloadHistory.keySet().iterator().next());
                    forgottenPreloadDeclarations++;
                }
            }
            lookup.remove(oldest.key, oldest);
            if (oldest.result != null) releaseResult(oldest);
            entries.remove(oldest);
            idle--;
        }
        for (int i = scopes.size() - 1; i >= 0; i--) {
            ShaderPreparationScope scope = scopes.get(i);
            if (scope.disposed && (!scope.submitted || scope.completion.isDone())) scopes.remove(i);
        }
    }

    public boolean hasPendingWork() {
        requireThread();
        if (active > 0) return true;
        synchronized (callbacks) { if (!callbacks.isEmpty()) return true; }
        return count(ShaderPreparationState.QUEUED) > 0;
    }

    public int queuedCount() { return count(ShaderPreparationState.QUEUED); }
    public int readyCount() { return count(ShaderPreparationState.READY); }
    public int failedCount() { return count(ShaderPreparationState.FAILED); }
    public int unsupportedCount() { return count(ShaderPreparationState.UNSUPPORTED); }

    /** Explicit diagnostic snapshot of failed/unsupported requests. Allocates on demand; use
     * outside drawing, for a loading report or developer diagnostic rather than per-frame polling. */
    public List<ShaderPreparationReport.Item> failures() {
        requireThread();
        List<ShaderPreparationReport.Item> result = new ArrayList<>();
        for (Entry entry : entries) {
            if (entry.state == ShaderPreparationState.FAILED || entry.state == ShaderPreparationState.UNSUPPORTED) {
                result.add(new ShaderPreparationReport.Item(entry.key.request, entry.state, entry.failure, entry.timings()));
            }
        }
        return List.copyOf(result);
    }

    /** Active operations, including native pipelines and results waiting for publication. */
    public int preparingCount() { requireThread(); return active; }

    public int compilingCount() {
        requireThread();
        int count = 0;
        for (int i = 0; i < entries.size(); i++) {
            Entry entry = entries.get(i);
            ShaderPreparationPhase phase = entry.phase();
            if (entry.operation != null && (phase == ShaderPreparationPhase.SOURCE
                    || phase == ShaderPreparationPhase.TRANSLATION || phase == ShaderPreparationPhase.COMPILATION)) count++;
        }
        return count;
    }

    private int count(ShaderPreparationState state) {
        requireThread();
        int count = 0;
        for (int i = 0; i < entries.size(); i++) if (entries.get(i).state == state) count++;
        return count;
    }

    /** Starts cancellation; call update() until the returned future completes before device teardown. */
    public FdxFuture<Void> disposeAsync() { dispose(); return drained; }

    @Override
    public void dispose() {
        requireThread();
        if (disposed) return;
        disposed = true;
        Throwable failure = null;
        for (int i = 0; i < entries.size(); i++) {
            try { invalidate(entries.get(i)); }
            catch (Throwable error) { failure = combine(failure, error); }
        }
        if (failure instanceof Error error) throw error;
        if (failure instanceof RuntimeException error) throw error;
        if (failure != null) throw new FdxException("Shader preparation cancellation failed", failure);
    }

    @Override
    public boolean isDisposed() { return disposed; }

    void requireThread() {
        if (Thread.currentThread() != applicationThread) {
            throw new FdxException("Shader preparation must be accessed on its application thread");
        }
    }

    void requireOpen() {
        requireThread();
        if (disposed) throw new FdxException("Shader preparation service is disposed");
        if (device.resourceDomain() != domain) throw new FdxException("Graphics resource domain changed");
    }

    <T> FdxFuture<T> newFuture() {
        return FdxFuture.pending(this::enqueueCallback);
    }

    void enqueueCallback(Runnable callback) { synchronized (callbacks) { callbacks.addLast(callback); } }

    private static Throwable combine(Throwable first, Throwable next) {
        if (first == null) return next;
        if (first != next) first.addSuppressed(next);
        return first;
    }

    static final class Entry {
        final Key key;
        final long sequence;
        ShaderPreparationState state = ShaderPreparationState.QUEUED;
        ShaderPreparationOperation operation;
        ShaderPreparedResult result;
        Throwable failure;
        int references;
        long lastUse;
        final long queuedNanos = System.nanoTime();
        long startedNanos, finishedNanos, firstDrawNanos;
        long firstDrawUpdate = -1;
        ShaderPreparationTrace trace;
        boolean runtimeDemand, loadingRejected, preloadDeclared, residencyLost;

        Entry(Key key, long sequence) { this.key = key; this.sequence = sequence; }

        ShaderPreparationTimings timings() {
            long end = finishedNanos != 0 ? finishedNanos : System.nanoTime();
            long queue = Math.max(0, (startedNanos != 0 ? startedNanos : end) - queuedNanos);
            long preparation = startedNanos != 0 ? Math.max(0, end - startedNanos) : 0;
            long first = firstDrawUpdate >= 0 ? Math.max(0, firstDrawNanos - queuedNanos) : -1;
            long afterReady = firstDrawUpdate >= 0 ? Math.max(0, firstDrawNanos - finishedNanos) : -1;
            return trace != null ? trace.snapshot(queue, preparation, first, afterReady, firstDrawUpdate)
                    : new ShaderPreparationTimings(queue, preparation, first, afterReady, firstDrawUpdate, null, null);
        }

        ShaderPreparationPhase phase() {
            return operation != null ? operation.phase()
                    : state == ShaderPreparationState.QUEUED
                        ? ShaderPreparationPhase.QUEUED : ShaderPreparationPhase.COMPLETE;
        }
    }

    static final class Key {
        final ShaderProvider provider;
        final long revision;
        final ShaderRequest request;
        final int hash;

        Key(ShaderProvider provider, long revision, ShaderRequest request) {
            if (revision < 0) throw new FdxException("Shader provider revision cannot be negative");
            this.provider = provider;
            this.revision = revision;
            this.request = request;
            hash = 31 * (31 * System.identityHashCode(provider) + Long.hashCode(revision)) + request.hashCode();
        }

        boolean matches(ShaderProvider provider, long revision, ShaderRequest request) {
            return this.provider == provider && this.revision == revision && this.request.equals(request);
        }

        @Override
        public int hashCode() { return hash; }
        @Override
        public boolean equals(Object other) {
            return other instanceof Key key && matches(key.provider, key.revision, key.request);
        }
    }
}
