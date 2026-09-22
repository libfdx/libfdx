package io.github.libfdx.graphics.shader.runtime;

import io.github.libfdx.core.FdxException;
import io.github.libfdx.core.FdxFuture;
import io.github.libfdx.core.ProviderId;
import io.github.libfdx.graphics.GraphicsDevice;
import io.github.libfdx.graphics.PrimitiveTopology;
import io.github.libfdx.graphics.RenderPassCompatibility;
import io.github.libfdx.graphics.RenderPipeline;
import io.github.libfdx.graphics.RenderTargetLayout;
import io.github.libfdx.graphics.shader.reflection.ShaderEntryPoint;
import io.github.libfdx.graphics.shader.reflection.ShaderReflection;
import io.github.libfdx.graphics.shader.reflection.ShaderResourceLayout;
import io.github.libfdx.graphics.shader.ShaderProfile;
import io.github.libfdx.graphics.shader.ShaderStage;
import io.github.libfdx.graphics.TextureFormat;
import io.github.libfdx.graphics.VertexAttribute;
import io.github.libfdx.graphics.VertexFormat;
import io.github.libfdx.graphics.VertexLayout;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicReference;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static io.github.libfdx.graphics.shader.runtime.ShaderPreparationCapabilities.Execution.*;
import static io.github.libfdx.graphics.shader.runtime.ShaderPreparationState.*;
import static org.junit.jupiter.api.Assertions.*;

class ShaderPreparationTest {
    @Test void timingDistinguishesReadinessDemandAndActualDrawAndFreezesSnapshots() {
        Fixture f = new Fixture(4, 8, 16);
        f.instrument = true;
        var capture = f.service.captureRuntime("timed");
        var scope = f.service.createScope("timed");
        var handle = scope.include(f, request("timed"));
        var complete = f.service.prepareAsync(scope.seal());
        f.service.update();
        Job job = f.jobs.getFirst();
        job.trace.enter(ShaderPreparationPhase.COMPILATION);
        job.trace.enter(ShaderPreparationPhase.PUBLICATION_WAIT);
        job.done = true; f.service.update();
        var ready = handle.timings();
        assertTrue(ready.phasesAvailable());
        assertEquals(-1, ready.firstDrawNanos());
        handle.recordDraws(origin("timed"), 2, false);
        assertEquals(-1, handle.timings().firstDrawNanos());
        handle.readyPass().recordDraw();
        var drawn = handle.timings();
        assertTrue(drawn.firstDrawNanos() >= drawn.queueNanos() + drawn.preparationNanos());
        assertEquals(f.service.frameIndex(), drawn.firstDrawUpdate());
        f.service.update(); handle.readyPass().recordDraw();
        assertEquals(drawn.firstDrawNanos(), handle.timings().firstDrawNanos());
        assertEquals(-1, ready.firstDrawNanos());
        assertEquals(-1, complete.get().items().getFirst().timings().firstDrawNanos());
        assertEquals(drawn.firstDrawNanos(), capture.snapshot().discoveries().getFirst().timings().firstDrawNanos());
        assertTrue(capture.snapshot().markdown().contains("First draw since enqueue"));
        assertEquals(ShaderPreparationPhase.COMPLETE, job.trace.phase());
        f.service.dispose();
    }

    @Test void cancelledNativeWorkKeepsItsTraceUntilDrainCompletes() {
        Fixture f = new Fixture(1, 8, 16);
        f.instrument = true;
        var handle = f.service.request(f, request("cancelled-worker"));
        f.service.update();
        Job job = f.jobs.getFirst();
        job.trace.enter(ShaderPreparationPhase.COMPILATION);
        var drained = f.service.disposeAsync();
        assertFalse(drained.isDone());
        job.done = true;
        f.service.update();
        assertTrue(drained.isDone());
        assertEquals(CANCELLED, handle.state());
        var timing = handle.timings();
        assertTrue(timing.phaseNanos(ShaderPreparationPhase.COMPILATION) > 0);
        assertEquals(-1, timing.firstDrawNanos());
        f.service.update();
        assertEquals(timing.preparationNanos(), handle.timings().preparationNanos());
        assertEquals(ShaderPreparationPhase.COMPLETE, job.trace.phase());
    }

    @Test void queuedCancellationAndUnsupportedHaveFrozenTimingsAndNoInventedPhases() {
        Fixture f = new Fixture(4, 8, 16);
        var handle = f.service.request(f, request("cancelled"));
        handle.dispose();
        long queue = handle.timings().queueNanos();
        f.service.update();
        assertEquals(queue, handle.timings().queueNanos());
        assertEquals(0, handle.timings().preparationNanos());
        assertFalse(handle.timings().phasesAvailable());
        assertEquals(-1, handle.timings().phaseNanos(ShaderPreparationPhase.COMPILATION));
        assertNull(handle.timings().cacheMetrics(ShaderCacheLayer.SOURCE));
        f.capabilities = new ShaderPreparationCapabilities(UNAVAILABLE, UNAVAILABLE, false, 0, false, false);
        var unsupported = f.service.request(f, request("unsupported"));
        assertEquals(0, unsupported.timings().queueNanos());
        assertEquals(-1, unsupported.timings().firstDrawNanos());
        f.service.dispose();
    }

    @Test void failedOperationRetainsMeasuredPhasesWithoutFirstDraw() {
        Fixture f = new Fixture(4, 8, 16);
        f.instrument = true;
        var handle = f.service.request(f, request("failure"));
        f.service.update();
        Job job = f.jobs.getFirst();
        job.trace.enter(ShaderPreparationPhase.COMPILATION);
        job.failure = new IllegalStateException("invalid shader"); job.done = true;
        f.service.update();
        var timing = handle.timings();
        assertEquals(FAILED, handle.state());
        assertTrue(timing.phaseNanos(ShaderPreparationPhase.COMPILATION) > 0);
        assertEquals(-1, timing.firstDrawNanos());
        f.service.update();
        assertEquals(timing.preparationNanos(), handle.timings().preparationNanos());
        assertEquals(timing.phaseNanos(ShaderPreparationPhase.COMPILATION),
                f.service.failures().getFirst().timings().phaseNanos(ShaderPreparationPhase.COMPILATION));
        f.service.dispose();
    }

    private static ShaderPreparationOrigin origin(String variant) {
        return new ShaderPreparationOrigin("test-renderer", "level/model", "material", "forward",
                new ShaderPreloadRecipe("test.factory", 1, "surface", Map.of("variant", variant), Map.of()));
    }

    @Test void captureObservesDrawDemandIncludingReadyHitsButNotPreloadOrPolling() {
        Fixture f = new Fixture(4, 8, 16);
        var capture = f.service.captureRuntime("level");
        var scope = f.service.createScope("level");
        var handle = scope.include(f, request("ready"));
        f.service.prepareAsync(scope.seal());
        f.service.update(); f.jobs.getFirst().done = true; f.service.update();
        assertNotNull(handle.readyPass());
        assertTrue(capture.snapshot().discoveries().isEmpty());
        List<ShaderPreloadDiscovery> callbacks = new ArrayList<>();
        capture.onDiscovery(callbacks::add);
        handle.recordDraws(origin("ready"), 4, false);
        assertTrue(callbacks.isEmpty());
        f.service.update();
        assertEquals(1, callbacks.size());
        assertEquals(ShaderPreloadDiscovery.Cause.NONE, callbacks.getFirst().cause());
        handle.recordDraws(origin("ready"), 3, false); f.service.update();
        assertEquals(1, callbacks.size());
        assertEquals(7, capture.snapshot().discoveries().getFirst().logicalDraws());
    }

    @Test void sharedRequirementsRetainContentOriginsAndEveryLogicalPreloadRole() {
        Fixture f = new Fixture(4, 8, 16);
        var capture = f.service.captureRuntime("shared content");
        var handle = f.service.request(f, request("shared"));
        ShaderPreparationOrigin definition = origin("shared");
        var reflection = new ShaderPreparationOrigin("test-renderer", "", "", "reflection",
                new ShaderPreloadRecipe("test.factory", 1, "reflection", definition.recipe().parameters(), Map.of()));
        List<ShaderPreloadDiscovery> callbacks = new ArrayList<>(); capture.onDiscovery(callbacks::add);
        handle.recordDraws(definition, "tree", "leaves", 1, true);
        handle.recordDraws(reflection, "bush", "leaves", 2, true);
        f.service.update();
        var snapshot = capture.snapshot();
        assertEquals(1, f.jobs.size()); assertEquals(1, callbacks.size());
        assertEquals(1, snapshot.discoveries().size());
        assertEquals(List.of("tree", "bush"), snapshot.discoveries().getFirst().origins().stream()
                .map(ShaderPreparationOrigin::content).toList());
        assertEquals(2, ShaderPreloadManifest.fromJson(snapshot.manifest().toJson()).recipes().size());
        assertTrue(snapshot.markdown().contains("bush")); assertTrue(snapshot.markdown().contains("leaves"));
        handle.recordDraws(definition, "grass", "leaves", 1, true);
        assertEquals(2, snapshot.discoveries().getFirst().origins().size(), "A published snapshot must not mutate");
        assertEquals(3, capture.snapshot().discoveries().getFirst().origins().size());
    }

    @Test void originLimitReportsIncompleteCaptureWithoutDroppingKnownRequirementDrawCounts() {
        Fixture f = new Fixture(4, 8, 16);
        var capture = f.service.captureRuntime("many models");
        var handle = f.service.request(f, request("shared"));
        ShaderPreparationOrigin definition = origin("shared");
        for (int i = 0; i < 18; i++) handle.recordDraws(definition, "model-" + i, "material", 1, true);
        var snapshot = capture.snapshot();
        assertEquals(16, snapshot.discoveries().getFirst().origins().size());
        assertEquals(18, snapshot.discoveries().getFirst().logicalDraws());
        assertEquals(2, snapshot.droppedDemands());
        assertFalse(ShaderPreloadManifest.fromJson(snapshot.manifest().toJson()).diagnostics().isEmpty());
    }

    @Test void captureManifestReplaysInFreshServiceWithNoRuntimePreparationMiss() {
        Fixture cold = new Fixture(4, 8, 16);
        var capture = cold.service.captureRuntime("forest");
        var runtime = cold.service.request(cold, request("new-variant"));
        runtime.recordDraws(origin("new-variant"), 6, true);
        cold.service.update(); cold.jobs.getFirst().done = true; cold.service.update();
        String json = capture.snapshot().manifest().toJson();
        assertEquals(json, ShaderPreloadManifest.fromJson(json).toJson());
        assertEquals(ShaderPreloadDiscovery.Cause.NOT_PRELOADED, capture.snapshot().discoveries().getFirst().cause());

        Fixture replay = new Fixture(4, 8, 16); // No entries, compiler artifacts or shared native state.
        var scope = replay.service.createScope("forest");
        ShaderPreloadImportReport imported = scope.include(ShaderPreloadManifest.fromJson(json), recipe ->
                ShaderPreloadResolver.Resolution.resolved(replay, request(recipe.parameters().get("variant"))));
        assertFalse(imported.hasUnresolvedEntries());
        var completion = replay.service.prepareAsync(scope.seal());
        replay.service.update(); assertEquals(1, replay.jobs.size());
        replay.jobs.getFirst().done = true; replay.service.update();
        assertTrue(completion.get().allReady());
        var replayCapture = replay.service.captureRuntime("forest replay");
        var ready = replay.service.request(replay, request("new-variant"));
        ready.recordDraws(origin("new-variant"), 6, ready.readyPass() == null);
        replay.service.update();
        var observed = replayCapture.snapshot().discoveries().getFirst();
        assertEquals(ShaderPreloadDiscovery.Cause.NONE, observed.cause());
        assertEquals(0, observed.skippedDraws());
        assertEquals(1, replay.jobs.size());
    }

    @Test void captureReportsPreloadTooLateAndLostResidencySeparately() {
        Fixture f = new Fixture(4, 8, 0);
        var preload = f.service.createScope("old level");
        preload.include(f, request("evicted"));
        f.service.prepareAsync(preload.seal());
        f.service.update(); f.jobs.getFirst().done = true; f.service.update();
        preload.dispose(); f.service.update();
        var capture = f.service.captureRuntime("next");
        var runtime = f.service.request(f, request("evicted"));
        runtime.recordDraws(origin("evicted"), 1, true);
        var late = f.service.createScope("late");
        late.include(f, request("late")).recordDraws(origin("late"), 1, true);
        assertEquals(ShaderPreloadDiscovery.Cause.RESIDENCY_LOST, capture.snapshot().discoveries().get(0).cause());
        assertEquals(ShaderPreloadDiscovery.Cause.PRELOAD_TOO_LATE, capture.snapshot().discoveries().get(1).cause());
    }

    @Test void captureAggregatesARequirementAcrossResidencyLossWithoutConsumingAnotherSlot() {
        Fixture f = new Fixture(4, 8, 0);
        var capture = f.service.captureRuntime("one requirement", 1);
        List<ShaderPreloadDiscovery> callbacks = new ArrayList<>(); capture.onDiscovery(callbacks::add);
        var scope = f.service.createScope("level");
        var original = scope.include(f, request("shared"));
        f.service.prepareAsync(scope.seal());
        f.service.update(); f.jobs.getFirst().done = true; f.service.update();
        original.recordDraws(origin("shared"), 2, false); f.service.update();
        var beforeEviction = capture.snapshot().discoveries().getFirst();
        scope.dispose(); f.service.update();

        var replacement = f.service.request(f, request("shared"));
        replacement.recordDraws(origin("shared"), 3, true); f.service.update();
        var pending = capture.snapshot();
        assertEquals(1, pending.discoveries().size());
        assertEquals(0, pending.droppedDemands(), "Recreated entries are the same captured requirement");
        assertEquals(1, callbacks.size());
        assertEquals(beforeEviction.id(), pending.discoveries().getFirst().id());
        assertEquals(beforeEviction.firstNeededFrame(), pending.discoveries().getFirst().firstNeededFrame());
        assertEquals(5, pending.discoveries().getFirst().logicalDraws());
        assertEquals(3, pending.discoveries().getFirst().skippedDraws());
        assertEquals(ShaderPreloadDiscovery.Cause.RESIDENCY_LOST, pending.discoveries().getFirst().cause());
        assertEquals(ShaderPreparationState.PREPARING, pending.discoveries().getFirst().state());
        assertEquals(ShaderPreloadDiscovery.Cause.NONE, beforeEviction.cause(), "Old snapshots remain immutable");
        f.jobs.getLast().done = true; f.service.update();
        assertEquals(ShaderPreparationState.READY, capture.snapshot().discoveries().getFirst().state());
        assertEquals(1, capture.snapshot().manifest().recipes().size());
    }

    @Test void captureRetryUpdatesOneDiscoveryAndOlderConsumersCannotRestoreTheFailedOutcome() {
        Fixture f = new Fixture(4, 8, 16);
        var capture = f.service.captureRuntime("retry");
        List<ShaderPreloadDiscovery> callbacks = new ArrayList<>(); capture.onDiscovery(callbacks::add);
        var original = f.service.request(f, request("retry"));
        original.recordDraws(origin("retry"), 1, true); f.service.update();
        f.jobs.getFirst().failure = new FdxException("rejected"); f.jobs.getFirst().done = true; f.service.update();
        var failed = capture.snapshot().discoveries().getFirst();
        assertEquals(ShaderPreloadDiscovery.Cause.FAILED, failed.cause());

        var replacement = f.service.retry(original);
        replacement.recordDraws(origin("retry"), 2, true); f.service.update();
        original.recordDraws(origin("retry"), 1, true);
        assertEquals(1, capture.snapshot().discoveries().size());
        assertEquals(ShaderPreparationState.PREPARING, capture.snapshot().discoveries().getFirst().state());
        f.jobs.getLast().done = true; f.service.update();
        replacement.recordDraws(origin("retry"), 4, false);
        original.recordDraws(origin("retry"), 1, true);
        var current = capture.snapshot().discoveries().getFirst();
        assertEquals(1, callbacks.size());
        assertEquals(failed.id(), current.id());
        assertEquals(9, current.logicalDraws());
        assertEquals(5, current.skippedDraws());
        assertEquals(ShaderPreparationState.READY, current.state());
        assertEquals("", current.failure());
        assertEquals(ShaderPreloadDiscovery.Cause.FAILED, failed.cause());
    }

    @Test void captureRevisionAndStructuralChangesRemainDistinctRequirements() {
        Fixture f = new Fixture(4, 8, 16);
        var capture = f.service.captureRuntime("configuration");
        f.service.request(f, request("a")).recordDraws(origin("a"), 1, true);
        f.revision++;
        f.service.request(f, request("a")).recordDraws(origin("a"), 1, true);
        f.service.request(f, request("b")).recordDraws(origin("b"), 1, true);
        assertEquals(3, capture.snapshot().discoveries().size());
        assertEquals(ShaderPreloadDiscovery.Cause.CONFIGURATION_CHANGED,
                capture.snapshot().discoveries().get(1).cause());
    }

    @Test void captureLimitAndDisposalDoNotCancelSharedNativeWork() {
        Fixture f = new Fixture(4, 8, 16);
        var capture = f.service.captureRuntime("bounded", 1);
        var a = f.service.request(f, request("a")); var b = f.service.request(f, request("b"));
        a.recordDraws(origin("a"), 1, true); b.recordDraws(origin("b"), 1, true);
        f.service.update();
        assertEquals(1, capture.snapshot().discoveries().size()); assertEquals(1, capture.snapshot().droppedDemands());
        var partial = ShaderPreloadManifest.fromJson(capture.snapshot().manifest().toJson());
        var imported = f.service.createScope("partial").include(partial, recipe ->
                ShaderPreloadResolver.Resolution.resolved(f, request(recipe.parameters().get("variant"))));
        assertEquals(1, imported.resolvedCount());
        assertTrue(imported.hasUnresolvedEntries());
        assertTrue(imported.diagnostics().getFirst().contains("incomplete capture"));
        capture.dispose();
        assertEquals(0, f.jobs.getFirst().cancels);
        f.jobs.forEach(job -> job.done = true); f.service.update();
        assertNotNull(a.readyPass()); assertNotNull(b.readyPass());
        assertEquals(ShaderPreparationState.PREPARING, capture.snapshot().discoveries().getFirst().state());
    }

    @Test void captureExportDeliversCompletionOnlyThroughApplicationUpdate() {
        Fixture f = new Fixture(4, 8, 16);
        var capture = f.service.captureRuntime("export");
        FdxFuture<Void> writing = FdxFuture.pending();
        var export = capture.exportAsync(snapshot -> { assertEquals("export", snapshot.label()); return writing; });
        List<String> callbacks = new ArrayList<>(); export.onSuccess(ignored -> callbacks.add("done"));
        writing.complete(null);
        assertFalse(export.isDone()); assertTrue(callbacks.isEmpty());
        f.service.update();
        assertTrue(export.isDone()); assertEquals(List.of("done"), callbacks);
    }

    @Test void proceduralInputsAndFailedCompilationRemainActionableInExportAndImport() {
        Fixture f = new Fixture(4, 8, 16);
        var capture = f.service.captureRuntime("procedural");
        var handle = f.service.request(f, request("unknown"));
        handle.recordDraws(ShaderPreparationOrigin.UNKNOWN, 1, true);
        f.service.update(); f.jobs.getFirst().failure = new FdxException("invalid WGSL");
        f.jobs.getFirst().done = true; f.service.update();
        var snapshot = capture.snapshot();
        assertEquals(ShaderPreloadDiscovery.Cause.FAILED, snapshot.discoveries().getFirst().cause());
        assertTrue(snapshot.markdown().contains("Correct the shader failure"));
        assertEquals(1, snapshot.manifest().recipes().size());
        var imported = f.service.createScope("import").include(snapshot.manifest(), recipe -> {
            throw new AssertionError("Unresolved procedural input must not be silently resolved");
        });
        assertTrue(imported.hasUnresolvedEntries());
        assertEquals(ShaderPreloadResolver.Status.REQUIRES_INPUT, imported.items().getFirst().status());
    }

    @Test void manifestOrderAndConditionsSurviveMergingAndRoundTrip() {
        var a = new ShaderPreloadRecipe("factory", 1, "surface", Map.of("z", "1", "a", "quote\""), Map.of("quality", "ultra"));
        var b = new ShaderPreloadRecipe("factory", 1, "surface", Map.of("a", "quote\"", "z", "1"), Map.of("quality", "low"));
        var first = new ShaderPreloadManifest(List.of("z", "a"), List.of(b, a, a));
        var second = new ShaderPreloadManifest(List.of("a", "z"), List.of(a, b));
        assertEquals(first.toJson(), second.toJson());
        assertEquals(2, ShaderPreloadManifest.fromJson(first.toJson()).recipes().size());
        assertEquals(first.toJson(), ShaderPreloadManifest.merge(List.of(first, second)).toJson());
    }

    @Test void mergedManifestRetainsSegmentMembershipAndCompleteness() {
        var forest = origin("forest").recipe(); var cave = origin("cave").recipe();
        var first = new ShaderPreloadManifest(List.of(new ShaderPreloadManifest.Segment("forest", List.of(forest), 3, 0)));
        var second = new ShaderPreloadManifest(List.of(new ShaderPreloadManifest.Segment("cave", List.of(cave), 0, 2)));
        var merged = ShaderPreloadManifest.fromJson(ShaderPreloadManifest.merge(List.of(first, second, first)).toJson());
        assertEquals(2, merged.recipes().size());
        assertEquals(List.of(forest), merged.select("forest").recipes());
        assertEquals(3, merged.select("forest").segments().getFirst().droppedDemands());
        assertEquals(2, merged.select("cave").segments().getFirst().forgottenPreloadDeclarations());
        assertEquals(2, merged.diagnostics().size());
        assertThrows(FdxException.class, () -> merged.select("unknown"));
    }
    private static final RenderTargetLayout TARGET = RenderTargetLayout.color(TextureFormat.RGBA8_UNORM);
    private static final ShaderResourceLayout RESOURCES = ShaderResourceLayout.all(
            ShaderReflection.builder(ShaderProfile.PORTABLE_WEBGPU)
                    .entryPoints(ShaderEntryPoint.builder("vs", ShaderStage.VERTEX).build(),
                            ShaderEntryPoint.builder("fs", ShaderStage.FRAGMENT).build()).build());

    @Test void scopeAndRuntimeShareWorkAndPublishOnlyAtUpdate() {
        Fixture f = new Fixture(2, 8, 0);
        var scope = f.service.createScope("level");
        var preload = scope.include(f, request("a"));
        assertSame(preload, scope.include(f, request("a")));
        var runtime = f.service.request(f, request("a"));
        assertEquals(1, scope.totalCount());
        var future = f.service.prepareAsync(scope.seal());
        List<ShaderPreparationReport> reports = new ArrayList<>();
        future.onSuccess(reports::add);
        assertTrue(f.jobs.isEmpty());
        assertNull(runtime.readyPass());
        f.service.update();
        assertEquals(1, f.jobs.size());
        assertEquals(PREPARING, runtime.state());
        f.jobs.getFirst().done = true;
        assertNull(runtime.readyPass());
        assertFalse(future.isDone());
        f.service.update();
        assertSame(preload.readyPass(), runtime.readyPass());
        assertEquals(1, reports.size());
        assertTrue(reports.getFirst().allReady());
        assertEquals(1, reports.getFirst().readyCount());
        assertFalse(f.service.hasPendingWork());
        scope.dispose();
        f.service.update();
        assertEquals(0, f.jobs.getFirst().released);
        runtime.dispose();
        f.service.update();
        assertEquals(1, f.jobs.getFirst().released);
    }

    @Test void failuresAreStickyAndDoNotAbandonOtherJobs() {
        Fixture f = new Fixture(1, 8, 16);
        var scope = f.service.createScope("mixed");
        var failed = scope.include(f, request("bad"));
        scope.include(f, request("good"));
        var future = f.service.prepareAsync(scope.seal());
        f.service.update();
        f.jobs.getFirst().failure = new FdxException("invalid shader");
        f.jobs.getFirst().done = true;
        f.service.update();
        assertEquals(FAILED, failed.state());
        assertEquals(2, f.jobs.size());
        f.jobs.getLast().done = true;
        f.service.update();
        assertEquals(1, future.get().failedCount());
        assertEquals(1, future.get().readyCount());
        assertFalse(future.get().allReady());
        assertFalse(f.service.hasPendingWork());
        assertSame(failed.failure(), f.service.request(f, request("bad")).failure());
        f.service.update();
        assertEquals(2, f.jobs.size());
        var retry = f.service.retry(failed);
        f.service.update();
        f.jobs.getLast().done = true;
        f.service.update();
        assertEquals(READY, retry.state());
        assertEquals(FAILED, failed.state());
        assertEquals(3, f.jobs.size());
    }

    @Test void boundsInFlightPublicationAndImmediateSubmissionFailures() {
        Fixture f = new Fixture(2, 1, 16);
        for (int i = 0; i < 6; i++) f.service.request(f, request("v" + i));
        f.service.update();
        assertEquals(2, f.jobs.size());
        assertEquals(2, f.service.preparingCount());
        f.jobs.forEach(job -> job.done = true);
        f.service.update();
        assertEquals(1, f.service.readyCount());
        assertEquals(3, f.jobs.size());
        assertEquals(2, f.service.preparingCount());

        Fixture rejected = new Fixture(2, 8, 16);
        rejected.failStart = true;
        for (int i = 0; i < 6; i++) rejected.service.request(rejected, request("v" + i));
        rejected.service.update();
        assertEquals(2, rejected.starts);
        assertEquals(2, rejected.service.failedCount());
        assertEquals(4, rejected.service.queuedCount());
    }

    @Test void emptyAndCacheHitScopesAndLateListenersDispatchThroughUpdate() throws Exception {
        Fixture f = new Fixture(2, 8, 16);
        var empty = f.service.prepareAsync(f.service.createScope("empty").seal());
        List<Thread> notified = new ArrayList<>();
        empty.onSuccess(report -> notified.add(Thread.currentThread()));
        assertFalse(empty.isDone());
        f.service.update();
        assertTrue(empty.get().allReady());
        Thread other = new Thread(() -> empty.onSuccess(report -> notified.add(Thread.currentThread())));
        other.start();
        other.join();
        assertEquals(1, notified.size());
        f.service.update();
        assertEquals(List.of(Thread.currentThread(), Thread.currentThread()), notified);

        var handle = f.service.request(f, request("a"));
        f.service.update();
        f.jobs.getFirst().done = true;
        f.service.update();
        var scope = f.service.createScope("cached");
        scope.include(f, request("a"));
        var future = f.service.prepareAsync(scope.seal());
        assertFalse(future.isDone());
        f.service.update();
        assertTrue(future.get().allReady());
        assertEquals(1, f.starts);
        assertNotNull(handle.readyPass());
    }

    @Test void callbackFailureDoesNotStarveOtherCallbacksAndRecursiveUpdateIsRejected() {
        Fixture f = new Fixture(1, 1, 16);
        List<String> events = new ArrayList<>();
        var first = f.service.prepareAsync(f.service.createScope("one").seal());
        var second = f.service.prepareAsync(f.service.createScope("two").seal());
        first.onSuccess(report -> {
            assertThrows(FdxException.class, f.service::update);
            events.add("first");
            throw new IllegalStateException("listener");
        });
        second.onSuccess(report -> events.add("second"));
        assertThrows(IllegalStateException.class, f.service::update);
        assertEquals(List.of("first"), events);
        assertTrue(f.service.hasPendingWork());
        f.service.update();
        assertEquals(List.of("first", "second"), events);
        assertFalse(f.service.hasPendingWork());
    }

    @Test void consumerCancellationDoesNotCancelSharedNativeWork() {
        Fixture f = new Fixture(1, 8, 0);
        var first = f.service.request(f, request("a"));
        var second = first.retain();
        f.service.update();
        first.dispose();
        first.dispose();
        assertEquals(CANCELLED, first.state());
        assertEquals(PREPARING, second.state());
        assertEquals(0, f.jobs.getFirst().cancels);
        f.jobs.getFirst().done = true;
        f.service.update();
        assertNotNull(second.readyPass());
        second.dispose();
        f.service.update();
        assertEquals(1, f.jobs.getFirst().released);
    }

    @Test void unobservedInFlightWorkCanBeReusedWithoutDuplicateAndThenRetired() {
        Fixture f = new Fixture(1, 8, 0);
        var first = f.service.request(f, request("a"));
        f.service.update();
        first.dispose();
        var arriving = f.service.request(f, request("a"));
        f.jobs.getFirst().done = true;
        f.service.update();
        assertEquals(1, f.starts);
        assertEquals(READY, arriving.state());
        arriving.dispose();
        f.service.update();
        assertEquals(1, f.jobs.getFirst().released);
    }

    @Test void queuedCancellationDoesNotStartWorkAndScopeReportsCancellation() {
        Fixture f = new Fixture(1, 8, 0);
        var scope = f.service.createScope("cancelled");
        scope.include(f, request("a"));
        var future = f.service.prepareAsync(scope.seal());
        scope.dispose();
        f.service.update();
        assertEquals(0, f.starts);
        assertEquals(1, future.get().cancelledCount());
        assertFalse(future.get().allReady());
    }

    @Test void shutdownDrainsUncancellableNativeOperationAndDisposesLateResultExactlyOnce() {
        Fixture f = new Fixture(1, 8, 0);
        var handle = f.service.request(f, request("a"));
        f.service.update();
        var drained = f.service.disposeAsync();
        f.service.dispose();
        f.service.update();
        assertEquals(CANCELLED, handle.state());
        assertEquals(1, f.jobs.getFirst().cancels);
        assertEquals(0, f.jobs.getFirst().disposals);
        assertFalse(drained.isDone());
        assertTrue(f.service.hasPendingWork());
        f.jobs.getFirst().done = true;
        f.service.update();
        assertTrue(drained.isDone());
        assertEquals(1, f.jobs.getFirst().released);
        assertEquals(1, f.jobs.getFirst().disposals);
        f.service.update();
        assertEquals(1, f.jobs.getFirst().released);
        assertThrows(FdxException.class, () -> f.service.request(f, request("a")));
    }

    @Test void revisionChangeRejectsLateResultButKeepsAlreadyReadyLeaseForHotReload() {
        Fixture f = new Fixture(2, 8, 0);
        var ready = f.service.request(f, request("ready"));
        var pending = f.service.request(f, request("pending"));
        f.service.update();
        f.jobs.getFirst().done = true;
        f.service.update();
        f.revision++;
        f.jobs.getLast().done = true;
        f.service.update();
        assertEquals(READY, ready.state());
        assertEquals(CANCELLED, pending.state());
        assertEquals(1, f.jobs.getLast().released);
        var replacement = f.service.request(f, request("ready"));
        f.service.update();
        assertEquals(PREPARING, replacement.state());
        assertNotNull(ready.readyPass());
        f.jobs.getLast().done = true;
        f.service.update();
        assertNotSame(ready.readyPass(), replacement.readyPass());
        ready.dispose();
        f.service.update();
        assertEquals(1, f.jobs.getFirst().released);
    }

    @Test void deviceGenerationChangeCancelsAndDrainsAndForeignDomainIsRejected() {
        Fixture f = new Fixture(1, 8, 0);
        Fixture foreign = new Fixture(1, 8, 0);
        assertThrows(FdxException.class, () -> f.service.request(foreign, request("a")));
        var handle = f.service.request(f, request("a"));
        f.service.update();
        f.domain = new Object();
        f.service.update();
        assertTrue(f.service.isDisposed());
        assertEquals(CANCELLED, handle.state());
        assertEquals(1, f.jobs.getFirst().cancels);
        f.jobs.getFirst().done = true;
        f.service.update();
        assertEquals(1, f.jobs.getFirst().released);
    }

    @Test void deviceGenerationChangeRetiresReadyLeasesAndRejectsLateCompletion() {
        Fixture f = new Fixture(2, 8, 0);
        var ready = f.service.request(f, request("ready"));
        var pending = f.service.request(f, request("pending"));
        f.service.update();
        f.jobs.getFirst().done = true;
        f.service.update();
        assertNotNull(ready.readyPass());
        f.domain = new Object();
        f.service.update();
        assertNull(ready.readyPass());
        assertEquals(CANCELLED, ready.state());
        assertEquals(CANCELLED, pending.state());
        assertEquals(1, f.jobs.getFirst().released);
        f.jobs.getLast().done = true;
        f.service.update();
        assertNull(pending.readyPass());
        assertEquals(1, f.jobs.getLast().released);
        ready.dispose();
        pending.dispose();
        f.service.update();
        assertEquals(1, f.jobs.getFirst().released);
        assertEquals(1, f.jobs.getLast().released);
    }

    @Test void invalidProviderResultIsReleasedAndNeverPublished() {
        Fixture f = new Fixture(1, 8, 0);
        var handle = f.service.request(f, request("a"));
        f.service.update();
        f.jobs.getFirst().target = RenderTargetLayout.color(TextureFormat.BGRA8_UNORM);
        f.jobs.getFirst().done = true;
        f.service.update();
        assertEquals(FAILED, handle.state());
        assertNull(handle.readyPass());
        assertEquals(1, f.jobs.getFirst().released);
        assertEquals(1, f.jobs.getFirst().disposals);
    }

    @Test void pinnedScopeSurvivesCapacityPressure() {
        Fixture f = new Fixture(4, 8, 1);
        var pinned = f.service.createScope("level");
        var retained = pinned.include(f, request("pinned"));
        List<PreparedShaderPass> idle = new ArrayList<>();
        for (int i = 0; i < 3; i++) idle.add(f.service.request(f, request("idle" + i)));
        f.service.update();
        f.jobs.forEach(job -> job.done = true);
        f.service.update();
        idle.forEach(PreparedShaderPass::dispose);
        f.service.update();
        assertNotNull(retained.readyPass());
        assertEquals(0, f.jobs.stream().filter(job -> job.request.variantKey().equals("pinned"))
                .findFirst().orElseThrow().released);
        assertEquals(2, f.jobs.stream().mapToInt(job -> job.released).sum());
    }

    @Test void unsupportedProvidersNeverCallSynchronousResolve() {
        Fixture f = new Fixture(1, 8, 16);
        f.capabilities = ShaderPreparationCapabilities.UNAVAILABLE;
        var scope = f.service.createScope("unsupported");
        var handle = scope.include(f, request("a"));
        var future = f.service.prepareAsync(scope.seal());
        f.service.update();
        assertEquals(UNSUPPORTED, handle.state());
        assertEquals(1, future.get().unsupportedCount());
        assertFalse(f.service.hasPendingWork());
        assertEquals(0, f.starts);
        assertNotNull(handle.failure());
    }

    @Test void loadingOnlyRequiresExplicitLoadingUpdateAndRuntimeCanUsePreloadedResult() {
        Fixture f = new Fixture(1, 8, 16);
        f.capabilities = new ShaderPreparationCapabilities(WORKERS, OWNER_THREAD, false, 1, false, false);
        var rejected = f.service.request(f, request("a"));
        assertEquals(UNSUPPORTED, rejected.state());
        var scope = f.service.createScope("loading");
        var preload = scope.include(f, request("a"));
        f.service.update();
        assertEquals(0, f.starts);
        f.service.updateLoading();
        assertEquals(1, f.starts);
        f.jobs.getFirst().done = true;
        f.service.update();
        assertSame(preload.readyPass(), f.service.request(f, request("a")).readyPass());
        assertEquals(UNSUPPORTED, rejected.state());
    }

    @Test void ordinaryUpdatesNeverAdvanceBlockingStagesAndCancellationOnlyDrains() {
        Fixture f = new Fixture(1, 8, 16);
        f.capabilities = new ShaderPreparationCapabilities(WORKERS, OWNER_THREAD, false, 1, false, false);
        var scope = f.service.createScope("loading");
        var handle = scope.include(f, request("a"));
        f.service.updateLoading();
        Job job = f.jobs.getFirst();
        for (int i = 0; i < 4; i++) f.service.update();
        assertEquals(0, job.loadingAdvances);
        assertEquals(PREPARING, handle.state());
        f.service.updateLoading();
        assertEquals(1, job.loadingAdvances);
        f.revision++;
        f.service.updateLoading();
        assertEquals(CANCELLED, handle.state());
        assertEquals(1, job.loadingAdvances);
        assertEquals(1, job.cancels);
        scope.dispose();
        var drained = f.service.disposeAsync();
        f.service.updateLoading();
        assertFalse(drained.isDone());
        assertEquals(1, job.loadingAdvances);
        job.done = true;
        f.service.update();
        assertTrue(drained.isDone());
        assertEquals(1, job.disposals);
    }

    @Test void requestsDistinguishStructuralConfigurationAndSealedScopesCannotGrow() {
        Fixture f = new Fixture(4, 8, 16);
        var scope = f.service.createScope("variants");
        scope.include(f, request("a"));
        scope.include(f, request("b"));
        scope.include(f, ShaderRequest.builder(ShaderPassId.FORWARD)
                .renderPass(RenderPassCompatibility.layout(TARGET))
                .variantKey("a").topology(PrimitiveTopology.LINE_LIST).build());
        assertEquals(3, scope.totalCount());
        assertThrows(FdxException.class, () -> f.service.prepareAsync(scope));
        scope.seal();
        assertThrows(FdxException.class, () -> scope.include(f, request("c")));
        f.service.update();
        assertEquals(3, f.starts);
    }

    @Test void ownerThreadContractRejectsWorkerAccess() throws Exception {
        Fixture f = new Fixture(1, 8, 16);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread worker = new Thread(() -> {
            try { f.service.request(f, request("a")); }
            catch (Throwable error) { failure.set(error); }
        });
        worker.start();
        worker.join();
        assertInstanceOf(FdxException.class, failure.get());
        assertEquals(0, f.service.queuedCount());
    }

    @Test void operationCleanupFailureStillPublishesOtherResultsAndDrains() {
        Fixture f = new Fixture(2, 8, 0);
        var first = f.service.request(f, request("first"));
        var second = f.service.request(f, request("second"));
        f.service.update();
        f.jobs.forEach(job -> job.done = true);
        f.jobs.getFirst().failDispose = true;
        assertThrows(IllegalStateException.class, f.service::update);
        assertNotNull(first.readyPass());
        assertNotNull(second.readyPass());
        assertEquals(0, f.service.preparingCount());
        var drained = f.service.disposeAsync();
        f.service.update();
        assertTrue(drained.isDone());
        assertEquals(1, f.jobs.getFirst().released);
        assertEquals(1, f.jobs.getLast().released);
    }

    @Test void runtimeDemandDoesNotStarvePreload() {
        Fixture f = new Fixture(1, 8, 16);
        var scope = f.service.createScope("preload");
        scope.include(f, request("level"));
        f.service.prepareAsync(scope.seal());
        for (int i = 0; i < 8; i++) f.service.request(f, request("visible" + i));
        for (int i = 0; i < 4; i++) {
            f.service.update();
            f.jobs.getLast().done = true;
        }
        assertTrue(f.jobs.stream().anyMatch(job -> job.request.variantKey().equals("level")));
        assertTrue(f.service.hasPendingWork());
    }

    @Test void equivalentVertexLayoutsShareAJobAndCallerArrayMutationDoesNotChangeIdentity() {
        Fixture f = new Fixture(2, 8, 16);
        var layout = VertexLayout.of(12,
                VertexAttribute.of(0, VertexFormat.FLOAT32X3, 0));
        var layouts = new VertexLayout[] {layout};
        var first = ShaderRequest.builder(ShaderPassId.FORWARD)
                .renderPass(RenderPassCompatibility.layout(TARGET)).vertexLayouts(layouts).build();
        int originalHash = first.hashCode();
        layouts[0] = null;
        first.vertexLayouts()[0] = null;
        var equivalent = ShaderRequest.builder(ShaderPassId.FORWARD)
                .renderPass(RenderPassCompatibility.layout(TARGET)).vertexLayouts(
                        VertexLayout.of(12, VertexAttribute.of(
                                0, VertexFormat.FLOAT32X3, 0))).build();
        assertEquals(first, equivalent);
        assertEquals(originalHash, equivalent.hashCode());
        var a = f.service.request(f, first);
        var b = f.service.request(f, equivalent);
        f.service.update();
        assertEquals(1, f.jobs.size());
        f.jobs.getFirst().done = true;
        f.service.update();
        assertSame(a.readyPass(), b.readyPass());
    }

    private static ShaderRequest request(String variant) {
        return ShaderRequest.builder(ShaderPassId.FORWARD)
                .renderPass(RenderPassCompatibility.layout(TARGET)).variantKey(variant).build();
    }

    /** Manual provider: no timing, native calls, sleeps, or synchronous resolve fallback. */
    private static final class Fixture implements ShaderProvider {
        Object domain = new Object();
        ShaderPreparationCapabilities capabilities =
                new ShaderPreparationCapabilities(WORKERS, WORKERS, true, 4, false, false);
        final List<Job> jobs = new ArrayList<>();
        final GraphicsDevice device;
        final ShaderPreparation service;
        long revision;
        int starts;
        boolean failStart, instrument;

        Fixture(int inFlight, int publications, int idle) {
            device = (GraphicsDevice) Proxy.newProxyInstance(GraphicsDevice.class.getClassLoader(),
                    new Class<?>[] {GraphicsDevice.class}, (proxy, method, args) -> switch (method.getName()) {
                        case "resourceDomain" -> domain;
                        case "providerId" -> ProviderId.of("manual-test");
                        case "shaderPreparationCapabilities" -> capabilities;
                        default -> throw new AssertionError("Unexpected device call: " + method);
                    });
            service = new ShaderPreparation(device, new ShaderPreparationOptions(inFlight, publications, idle));
        }
        @Override public GraphicsDevice preparationDevice() { return device; }
        @Override public boolean supports(ShaderRequest request) { return true; }
        @Override public long revision() { return revision; }
        @Override public ResolvedShaderPass resolve(ShaderRequest request) {
            throw new AssertionError("Synchronous resolution is forbidden");
        }
        @Override public ShaderPreparationOperation beginPreparation(ShaderRequest request) {
            starts++;
            if (failStart) throw new FdxException("submission failed");
            Job job = new Job(request, revision);
            if (instrument) job.trace = new ShaderPreparationTrace();
            jobs.add(job);
            return job;
        }
    }

    private static final class Job implements ShaderPreparationOperation {
        final ShaderRequest request;
        final long revision;
        RenderTargetLayout target = TARGET;
        ShaderPreparationTrace trace;
        @Override public ShaderPreparationTrace trace() { return trace; }
        boolean done, failDispose;
        RuntimeException failure;
        int cancels, disposals, released, finishes, loadingAdvances;

        Job(ShaderRequest request, long revision) { this.request = request; this.revision = revision; }
        @Override public boolean isDone() { return done; }
        @Override public void advanceLoading() { loadingAdvances++; }
        @Override public ShaderPreparationPhase phase() { return ShaderPreparationPhase.COMPILATION; }
        @Override public ShaderPreparedResult finish() {
            assertTrue(done);
            assertEquals(0, finishes++);
            if (failure != null) throw failure;
            RenderPipeline pipeline = (RenderPipeline) Proxy.newProxyInstance(RenderPipeline.class.getClassLoader(),
                    new Class<?>[] {RenderPipeline.class}, (proxy, method, args) -> switch (method.getName()) {
                        case "targetLayout" -> target;
                        case "dispose" -> { released++; yield null; }
                        case "isDisposed" -> released != 0;
                        default -> throw new AssertionError(method);
                    });
            return new ShaderPreparedResult(ResolvedShaderPass.of(request.passId(), pipeline, RESOURCES, revision), pipeline);
        }
        @Override public void cancel() { cancels++; }
        @Override public void dispose() {
            assertTrue(done);
            disposals++;
            if (failDispose) throw new IllegalStateException("operation cleanup failed");
        }
        @Override public boolean isDisposed() { return disposals != 0; }
    }
}
