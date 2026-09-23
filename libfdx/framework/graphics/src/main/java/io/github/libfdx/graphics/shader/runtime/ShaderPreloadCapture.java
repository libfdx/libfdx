package io.github.libfdx.graphics.shader.runtime;

import io.github.libfdx.core.Disposable;
import io.github.libfdx.core.FdxFuture;
import java.util.ArrayList;
import java.util.function.Consumer;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;

/** Application-owned bounded runtime observation. Disposal stops observation without cancelling
 * preparation and preserves a final export snapshot. Native resources are never pinned by capture.
 * Only explicit draw demand is observed; preload collection and ready polling are not gameplay. */
public final class ShaderPreloadCapture implements Disposable {
    @FunctionalInterface
    public interface Destination {
        /** Must return without encoding large payloads or doing I/O on the caller. The platform
         * adapter writes manifest JSON and Markdown from this one immutable snapshot. */
        FdxFuture<Void> writeAsync(ShaderPreloadExport snapshot);
    }

    private final ShaderPreparation owner;
    private final String label, provider;
    private final int limit;
    private final HashMap<ShaderPreparation.Key, Observation> lookup = new HashMap<>();
    private final List<Observation> observations = new ArrayList<>();
    private Consumer<ShaderPreloadDiscovery> listener;
    private long dropped;
    private int originCount;
    private boolean disposed;
    private ShaderPreloadExport frozen;

    ShaderPreloadCapture(ShaderPreparation owner, String label, int limit) {
        this.owner = owner; this.label = Objects.requireNonNull(label); this.limit = limit;
        provider = owner.device().providerId().value();
    }

    /** One initial callback per unique requirement, through update. Later timings/outcomes are
     * available by snapshot using the same discovery ID. Register before requesting observations. */
    public ShaderPreloadCapture onDiscovery(Consumer<ShaderPreloadDiscovery> listener) {
        owner.requireThread(); this.listener = Objects.requireNonNull(listener); return this;
    }

    void observe(ShaderPreparation.Entry entry, ShaderPreparationOrigin definition, String content, String material,
            int draws, boolean skipped) {
        Observation observation = lookup.get(entry.key);
        if (observation == null) {
            if (observations.size() == limit || originCount == 65536) { dropped++; return; }
            ShaderPreparationOrigin origin = labeled(definition, content, material);
            observation = new Observation(observations.size() + 1L, entry, origin, owner.frameIndex(), cause(entry, origin));
            originCount++;
            lookup.put(entry.key, observation); observations.add(observation);
        } else {
            // Retry and residency replacement keep one discovery. Older retained consumers
            // still count as demand but cannot overwrite the latest observed attempt's outcome.
            if (entry.sequence > observation.entry.sequence) {
                observation.cause = cause(entry, labeled(definition, content, material));
                observation.entry = entry;
                observation.outcome = null;
            }
            if (!observation.hasOrigin(definition, content, material)) {
                if (observation.origins.size() == 16 || originCount == 65536) dropped++;
                else { observation.origins.add(labeled(definition, content, material)); originCount++; }
            }
        }
        observation.draws += draws;
        if (skipped) observation.skipped += draws;
    }

    private ShaderPreloadDiscovery.Cause cause(ShaderPreparation.Entry entry, ShaderPreparationOrigin origin) {
        if (entry.preloadDeclared) return entry.state == ShaderPreparationState.READY
                ? ShaderPreloadDiscovery.Cause.NONE : ShaderPreloadDiscovery.Cause.PRELOAD_TOO_LATE;
        if (entry.residencyLost) return ShaderPreloadDiscovery.Cause.RESIDENCY_LOST;
        return changed(entry, origin) ? ShaderPreloadDiscovery.Cause.CONFIGURATION_CHANGED
                : ShaderPreloadDiscovery.Cause.NOT_PRELOADED;
    }

    private static ShaderPreparationOrigin labeled(ShaderPreparationOrigin definition, String content, String material) {
        return definition.content().equals(content) && definition.material().equals(material) ? definition
                : new ShaderPreparationOrigin(definition.renderer(), content, material, definition.group(), definition.recipe());
    }

    private boolean changed(ShaderPreparation.Entry entry, ShaderPreparationOrigin origin) {
        for (int i = 0; i < observations.size(); i++) {
            Observation previous = observations.get(i);
            if (previous.entry.key.provider != entry.key.provider) continue;
            if (previous.entry.key.revision != entry.key.revision) return true;
            if (!origin.content().isEmpty() && previous.hasContent(origin.content())
                    && previous.entry.key.request.passId().equals(entry.key.request.passId())
                    && !previous.entry.key.request.equals(entry.key.request)) return true;
        }
        return false;
    }

    void publish() {
        for (int i = 0; i < observations.size(); i++) {
            Observation observation = observations.get(i);
            observation.settle();
            if (listener != null && !observation.delivered) {
                observation.delivered = true;
                ShaderPreloadDiscovery discovery = discovery(observation);
                Consumer<ShaderPreloadDiscovery> callback = listener;
                owner.enqueueCallback(() -> { if (!disposed) callback.accept(discovery); });
            }
        }
    }

    /** Explicit consistent diagnostic copy, potentially allocating. Do not call per draw. */
    public ShaderPreloadExport snapshot() {
        owner.requireThread();
        if (frozen != null) return frozen;
        List<ShaderPreloadDiscovery> results = new ArrayList<>(observations.size());
        for (Observation observation : observations) { observation.settle(); results.add(discovery(observation)); }
        return new ShaderPreloadExport(label, results, dropped, owner.forgottenPreloadDeclarations());
    }

    public FdxFuture<Void> exportAsync(Destination destination) {
        owner.requireThread();
        FdxFuture<Void> result = owner.newFuture();
        try {
            Objects.requireNonNull(destination.writeAsync(snapshot())).onSuccess(ignored ->
                    owner.enqueueCallback(() -> result.complete(null))).onFailure(failure ->
                    owner.enqueueCallback(() -> result.completeExceptionally(failure)));
        } catch (RuntimeException failure) { owner.enqueueCallback(() -> result.completeExceptionally(failure)); }
        return result;
    }

    private ShaderPreloadDiscovery discovery(Observation observation) {
        ShaderPreparation.Entry entry = observation.entry;
        ShaderPreparationState state = observation.outcome != null ? observation.outcome : entry.state;
        ShaderPreloadDiscovery.Cause cause = state == ShaderPreparationState.FAILED ? ShaderPreloadDiscovery.Cause.FAILED
                : state == ShaderPreparationState.UNSUPPORTED ? ShaderPreloadDiscovery.Cause.UNSUPPORTED : observation.cause;
        long end = entry.finishedNanos != 0 ? entry.finishedNanos : System.nanoTime();
        long queue = (entry.startedNanos != 0 ? entry.startedNanos : end) - entry.queuedNanos;
        long preparation = entry.startedNanos != 0 ? end - entry.startedNanos : 0;
        return new ShaderPreloadDiscovery(observation.id, observation.origin, observation.origins, entry.key.request, provider,
                observation.frame, observation.initiallyReady, observation.preloaded, cause, state,
                observation.draws, observation.skipped, Math.max(0, queue), Math.max(0, preparation),
                entry.failure != null ? entry.failure.toString() : "", entry.timings());
    }

    @Override
    public void dispose() {
        owner.requireThread(); if (disposed) return;
        frozen = snapshot(); disposed = true; owner.removeCapture(this);
        lookup.clear(); observations.clear();
    }
    @Override
    public boolean isDisposed() { return disposed; }

    private static final class Observation {
        final long id, frame;
        ShaderPreparation.Entry entry;
        final ShaderPreparationOrigin origin;
        final List<ShaderPreparationOrigin> origins = new ArrayList<>();
        ShaderPreloadDiscovery.Cause cause;
        final boolean initiallyReady, preloaded;
        ShaderPreparationState outcome;
        long draws, skipped;
        boolean delivered;
        Observation(long id, ShaderPreparation.Entry entry, ShaderPreparationOrigin origin, long frame, ShaderPreloadDiscovery.Cause cause) {
            this.id = id; this.entry = entry; this.origin = origin; this.frame = frame; this.cause = cause;
            origins.add(origin);
            initiallyReady = entry.state == ShaderPreparationState.READY; preloaded = entry.preloadDeclared;
            settle();
        }
        void settle() { if (outcome == null && entry.state.terminal()) outcome = entry.state; }
        boolean hasContent(String content) {
            for (int i = 0; i < origins.size(); i++) if (origins.get(i).content().equals(content)) return true;
            return false;
        }
        boolean hasOrigin(ShaderPreparationOrigin definition, String content, String material) {
            for (int i = 0; i < origins.size(); i++) {
                ShaderPreparationOrigin candidate = origins.get(i);
                if (candidate.content().equals(content) && candidate.material().equals(material)
                        && candidate.renderer().equals(definition.renderer()) && candidate.group().equals(definition.group())
                        && Objects.equals(candidate.recipe(), definition.recipe())) return true;
            }
            return false;
        }
    }
}
