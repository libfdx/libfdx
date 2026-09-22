package io.github.libfdx.assets;

import io.github.libfdx.core.Disposable;
import io.github.libfdx.core.FdxException;
import io.github.libfdx.core.FdxFuture;
import io.github.libfdx.collections.ObjectMap;
import io.github.libfdx.files.FileHandle;
import io.github.libfdx.files.FileSystem;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

import static org.junit.jupiter.api.Assertions.*;

final class DefaultAssetManagerSchedulingTest {
    private final Map<String, FdxFuture<byte[]>> reads = new HashMap<>();
    private final List<String> events = new ArrayList<>();
    private final Map<String, Asset> built = new HashMap<>();
    private final FileSystem files = (FileSystem)Proxy.newProxyInstance(FileSystem.class.getClassLoader(),
            new Class<?>[] {FileSystem.class}, (proxy, method, args) -> file((String)args[0]));
    private DefaultAssetManager manager = new DefaultAssetManager(files);

    @AfterEach
    void dispose() {
        manager.dispose();
    }

    @Test
    void asynchronousFuturePreparationQueuesCompletionAndCleansLateOwnedResults() throws Exception {
        FdxFuture<Asset> provider = FdxFuture.pending();
        Thread app = Thread.currentThread();
        boolean[] started = {false}, delivered = {false};
        register((context, descriptor) -> context.asyncFuture(() -> { started[0]=true; return provider; }));
        AssetLease<Asset> lease = manager.acquire(descriptor("stream"));
        lease.future().onSuccess(value -> { assertSame(app,Thread.currentThread()); delivered[0]=true; });
        assertFalse(started[0]);
        manager.update(1,Long.MAX_VALUE); assertTrue(started[0]);
        Asset opened = asset("stream");
        Thread worker = new Thread(() -> provider.complete(opened)); worker.start(); worker.join();
        assertFalse(delivered[0]); assertFalse(lease.isLoaded());
        drainOneStepAtATime(); assertTrue(delivered[0]); assertSame(opened,lease.asset());
        lease.dispose(); assertEquals(1,opened.disposed);

        FdxFuture<Asset> delayed = FdxFuture.pending();
        register((context,descriptor) -> context.asyncFuture(() -> delayed));
        AssetLease<Asset> cancelled=manager.acquire(descriptor("cancelled-open"));
        manager.update(1,Long.MAX_VALUE); cancelled.dispose();
        Asset late=asset("late");
        worker=new Thread(() -> delayed.complete(late)); worker.start(); worker.join();
        drainOneStepAtATime(); assertEquals(1,late.disposed); assertTrue(cancelled.future().isFailed());
    }

    @Test
    void delayedDependencyAndParentFinalizationShareTheBudget() {
        reads.put("B", FdxFuture.pending());
        Thread applicationThread = Thread.currentThread();
        register((context, descriptor) -> {
            if (descriptor.path().equals("A")) {
                FdxFuture<Asset> dependency = context.dependency(descriptor("B"));
                return context.completeOnUpdate(() -> {
                    assertEquals(applicationThread, Thread.currentThread());
                    assertTrue(dependency.isDone());
                    assertEquals("B", dependency.get().name);
                    return asset("A");
                });
            }
            if (descriptor.path().equals("C")) {
                return context.completeOnUpdate(() -> asset("C"));
            }
            FdxFuture<Asset> result = FdxFuture.pending();
            context.readBytes(context.files().internal("B"))
                    .onSuccess(bytes -> context.async(() -> asset("B"))
                            .onSuccess(result::complete).onFailure(result::completeExceptionally))
                    .onFailure(result::completeExceptionally);
            return result;
        });
        AssetHandle<Asset> a = manager.load(descriptor("A"));
        a.future().onSuccess(value -> assertEquals(applicationThread, Thread.currentThread()));
        AssetHandle<Asset> c = manager.load(descriptor("C"));
        for (int i = 0; i < 8; i++) {
            assertFalse(manager.update(1, Long.MAX_VALUE));
            assertTrue(manager.lastUpdateTaskCount() <= 1);
        }
        assertFalse(a.isLoaded());
        assertTrue(c.isLoaded(), "unrelated ready work must progress while B is pending");
        assertEquals(List.of("create:C"), events);

        reads.get("B").complete(new byte[] {1});
        assertFalse(a.isLoaded());
        drainOneStepAtATime();
        assertTrue(a.isLoaded());
        assertEquals(List.of("create:C", "create:B", "create:A"), events);
    }

    @Test
    void timeBudgetChecksBetweenNonPreemptibleStepsAndZeroDoesNoWork() {
        long[] time = {0L};
        manager = new DefaultAssetManager(files, null, () -> time[0]);
        register((context, descriptor) -> context.completeOnUpdate(() -> {
            time[0] += 6L;
            return asset(descriptor.path());
        }));
        for (int i = 0; i < 4; i++) {
            manager.load(descriptor("asset" + i));
        }
        assertFalse(manager.update(0, 100L));
        assertFalse(manager.update(10, 0L));
        assertEquals(0, built.size());
        assertFalse(manager.update(10, 10L));
        assertEquals(2, built.size());
        assertEquals(2, manager.lastUpdateTaskCount());
        assertEquals(12L, manager.lastUpdateNanos());
        assertEquals(6L, manager.lastUpdateMaxTaskNanos());
        assertThrows(FdxException.class, () -> manager.update(-1, 5L));
        assertThrows(FdxException.class, () -> manager.update(1, -1L));
        manager.finishLoading();
    }

    @Test
    void diamondDependenciesLoadOnceAndOutliveIndividualParents() {
        Map<String, String[]> graph = Map.of("A", new String[] {"B", "C"},
                "B", new String[] {"D"}, "C", new String[] {"D"});
        graphLoader(graph);
        manager.load(descriptor("A"));
        manager.load(descriptor("C"));
        drainOneStepAtATime();
        assertEquals(List.of("create:D", "create:B", "create:C", "create:A"), events);

        manager.unload("D");
        assertFalse(built.get("D").isDisposed());
        manager.unload("A");
        assertEquals(1, built.get("A").disposed);
        assertEquals(1, built.get("B").disposed);
        assertEquals(0, built.get("C").disposed);
        assertEquals(0, built.get("D").disposed);
        manager.unload("C");
        assertEquals(1, built.get("C").disposed);
        assertEquals(1, built.get("D").disposed);
        assertTrue(events.indexOf("dispose:C") < events.indexOf("dispose:D"));
    }

    @Test
    void detectsSelfAndNestedCyclesWithoutHanging() {
        for (Map<String, String[]> graph : List.of(Map.of("A", new String[] {"A"}),
                Map.of("A", new String[] {"B"}, "B", new String[] {"C"}, "C", new String[] {"A"}))) {
            manager.dispose();
            manager = new DefaultAssetManager(files);
            graphLoader(graph);
            AssetHandle<Asset> handle = manager.load(descriptor("A"));
            drainOneStepAtATime();
            assertEquals(AssetStatus.FAILED, handle.status());
            Throwable error = assertThrows(FdxException.class, handle.future()::get);
            while (error.getCause() != null) {
                error = error.getCause();
            }
            assertTrue(error.getMessage().contains("cycle"));
            assertTrue(error.getMessage().contains("A"));
            assertTrue(built.isEmpty());
        }
    }

    @Test
    void detectsCyclesDiscoveredInLaterPreparationCompletions() {
        register((context, descriptor) -> {
            FdxFuture<Asset> result = FdxFuture.pending();
            context.async(() -> descriptor.path().equals("A") ? "B" : "A")
                    .onSuccess(dependency -> {
                        try {
                            context.dependency(descriptor(dependency));
                            context.completeOnUpdate(() -> asset(descriptor.path()))
                                    .onSuccess(result::complete).onFailure(result::completeExceptionally);
                        } catch (Throwable error) {
                            result.completeExceptionally(error);
                        }
                    }).onFailure(result::completeExceptionally);
            return result;
        });
        AssetHandle<Asset> a = manager.load(descriptor("A"));
        AssetHandle<Asset> b = manager.load(descriptor("B"));
        drainOneStepAtATime();
        assertTrue(a.future().isFailed());
        assertTrue(b.future().isFailed());
        assertTrue(built.isEmpty());
    }

    @Test
    void dependencyFailurePreventsFinalizationAndReleasesPrematureLegacyResult() {
        FdxFuture<Asset> dependency = FdxFuture.pending();
        register((context, descriptor) -> {
            if (descriptor.path().equals("B")) {
                return dependency;
            }
            context.dependency(descriptor("B"));
            if (descriptor.path().equals("legacy")) {
                return FdxFuture.completed(asset("legacy"));
            }
            return context.completeOnUpdate(() -> asset("A"));
        });
        AssetHandle<Asset> a = manager.load(descriptor("A"));
        AssetHandle<Asset> legacy = manager.load(descriptor("legacy"));
        manager.update();
        assertFalse(legacy.isLoaded());
        dependency.completeExceptionally(new FdxException("Cannot read B"));
        drainOneStepAtATime();
        assertTrue(a.future().isFailed());
        assertTrue(legacy.future().isFailed());
        assertFalse(built.containsKey("A"));
        assertEquals(1, built.get("legacy").disposed);
        assertNull(manager.find("B", Asset.class));
    }

    @Test
    void sharedPendingDependencySurvivesReleaseAndOldAttemptsCannotPublish() {
        FdxFuture<byte[]> oldRead = FdxFuture.pending();
        reads.put("B", oldRead);
        register((context, descriptor) -> {
            if (!descriptor.path().equals("B")) {
                context.dependency(descriptor("B"));
                return context.completeOnUpdate(() -> asset(descriptor.path()));
            }
            FdxFuture<Asset> result = FdxFuture.pending();
            context.readBytes(file("B")).onSuccess(bytes -> result.complete(asset("B")))
                    .onFailure(result::completeExceptionally);
            return result;
        });
        AssetHandle<Asset> oldA = manager.load(descriptor("A"));
        AssetHandle<Asset> c = manager.load(descriptor("C"));
        manager.update();
        manager.unload("A");
        assertTrue(oldA.future().isFailed());
        assertFalse(c.future().isDone());
        oldRead.complete(new byte[0]);
        drainOneStepAtATime();
        assertTrue(c.isLoaded());
        manager.unload("C");
        assertEquals(1, built.get("B").disposed);

        FdxFuture<byte[]> abandonedRead = FdxFuture.pending();
        reads.put("B", abandonedRead);
        manager.load(descriptor("A"));
        manager.update();
        manager.unload("A");
        reads.put("B", FdxFuture.pending());
        AssetHandle<Asset> newA = manager.load(descriptor("A"));
        manager.update();
        int beforeLate = events.size();
        abandonedRead.complete(new byte[0]);
        manager.update();
        assertFalse(newA.isLoaded());
        assertEquals(beforeLate, events.size());
        reads.get("B").complete(new byte[0]);
        drainOneStepAtATime();
        assertTrue(newA.isLoaded());
    }

    @Test
    void disposalReleasesAQueuedPreparationResultBeforeItIsDelivered() {
        Asset prepared = new Asset("prepared");
        register((context, descriptor) -> context.async(() -> prepared));
        AssetHandle<Asset> handle = manager.load(descriptor("A"));
        manager.update(1, Long.MAX_VALUE);
        assertFalse(handle.isLoaded());
        manager.dispose();
        assertEquals(1, prepared.disposed);
        assertTrue(handle.future().isFailed());
    }

    @Test
    void cancellationInsideFinalizationDisposesItsNewResultOnce() {
        Asset prepared = new Asset("prepared");
        register((context, descriptor) -> context.completeOnUpdate(() -> {
            manager.unload(descriptor.path());
            return prepared;
        }));
        AssetHandle<Asset> handle = manager.load(descriptor("A"));
        manager.update();
        assertEquals(AssetStatus.UNLOADED, handle.status());
        assertEquals(1, prepared.disposed);
    }

    @Test
    void cancellationBetweenFinalizationAndPublicationDisposesParentBeforeDependencies() {
        graphLoader(Map.of("A", new String[] {"B"}));
        AssetHandle<Asset> a = manager.load(descriptor("A"));
        for (int i = 0; i < 30 && !built.containsKey("A"); i++) {
            manager.update(1, Long.MAX_VALUE);
        }
        assertNotNull(built.get("A"));
        assertFalse(a.future().isDone());
        manager.unload("A");
        assertEquals(1, built.get("A").disposed);
        assertEquals(1, built.get("B").disposed);
        assertTrue(events.indexOf("dispose:A") < events.indexOf("dispose:B"));
        drainOneStepAtATime();
        assertEquals(1, built.get("A").disposed);
    }

    @Test
    void executorBackpressureDefersOncePerUpdateAndAllowsReadyWork() {
        class ControlledExecutor implements AssetExecutor {
            int attempts;
            boolean accepting;
            Runnable task;
            @Override public boolean submit(Runnable next) {
                attempts++;
                if (!accepting) { return false; }
                task = next;
                return true;
            }
            @Override public void dispose() { }
            @Override public boolean isDisposed() { return false; }
        }
        ControlledExecutor executor = new ControlledExecutor();
        manager = new DefaultAssetManager(files, executor);
        register((context, descriptor) -> descriptor.path().equals("A")
                ? context.async(() -> asset("A")) : context.completeOnUpdate(() -> asset("B")));
        AssetHandle<Asset> a = manager.load(descriptor("A"));
        AssetHandle<Asset> b = manager.load(descriptor("B"));
        assertFalse(manager.update());
        assertEquals(1, executor.attempts);
        assertTrue(b.isLoaded());
        assertFalse(manager.update());
        assertEquals(2, executor.attempts);
        executor.accepting = true;
        manager.update();
        executor.task.run();
        drainOneStepAtATime();
        assertTrue(a.isLoaded());
    }

    @Test
    void rejectsRecursiveUpdatesAndLateDependencyDiscovery() {
        register((context, descriptor) -> context.completeOnUpdate(() -> {
            assertThrows(FdxException.class, () -> manager.update());
            assertThrows(FdxException.class, () -> manager.finishLoading());
            context.dependency(descriptor("B"));
            return asset("A");
        }));
        AssetHandle<Asset> a = manager.load(descriptor("A"));
        drainOneStepAtATime();
        assertTrue(a.future().isFailed());
        assertTrue(assertThrows(FdxException.class, a.future()::get).getMessage().contains("before finalization"));
    }

    @Test
    void copiedOptionsCoalesceCompatibleRequestsAndRejectConflictingConfiguration() {
        graphLoader(Map.of());
        ObjectMap<String, Object> options = new ObjectMap<>();
        options.put("mode", "first");
        AssetDescriptor<Asset> first = AssetDescriptor.of("A", Asset.class, options.view());
        options.put("mode", "second");
        AssetDescriptor<Asset> conflicting = AssetDescriptor.of("A", Asset.class, options.view());
        AssetHandle<Asset> handle = manager.load(first);
        assertSame(handle, manager.load(first));
        assertThrows(FdxException.class, () -> manager.load(conflicting));
        assertThrows(FdxException.class, () -> graphLoader(Map.of()));
        manager.finishLoading();
        manager.unload("A");
        assertNotSame(handle, manager.load(conflicting));
        manager.finishLoading();
    }

    @Test
    void budgetedUpdatesRemainPendingWhileExecutorIsFull() {
        AssetExecutor full = new AssetExecutor() {
            @Override public boolean submit(Runnable task) { return false; }
            @Override public void dispose() { }
            @Override public boolean isDisposed() { return false; }
        };
        manager = new DefaultAssetManager(files, full);
        register((context, descriptor) -> context.async(() -> asset("A")));
        manager.load(descriptor("A"));
        for (int i = 0; i < 2_000; i++) {
            assertFalse(manager.update(1, Long.MAX_VALUE));
        }
        assertFalse(manager.update(0, 0));
    }

    private void graphLoader(Map<String, String[]> graph) {
        register((context, descriptor) -> {
            for (String dependency : graph.getOrDefault(descriptor.path(), new String[0])) {
                context.dependency(descriptor(dependency));
            }
            return context.completeOnUpdate(() -> asset(descriptor.path()));
        });
    }

    @Test
    void disposalContinuesThroughThrowingResourcesAndCancellationCallbacks() {
        graphLoader(Map.of("A", new String[] {"B", "C"}));
        manager.load(descriptor("A"));
        manager.finishLoading();
        built.get("A").throwOnDispose = true;
        built.get("C").throwOnDispose = true;
        assertThrows(FdxException.class, manager::dispose);
        assertTrue(manager.isDisposed());
        for (Asset asset : built.values()) {
            assertEquals(1, asset.disposed);
        }

        manager = new DefaultAssetManager(files);
        List<FdxFuture<Asset>> pending = new ArrayList<>();
        register((context, descriptor) -> {
            for (int i = 0; i < 3; i++) {
                FdxFuture<Asset> task = context.async(() -> asset("unused"));
                task.onFailure(error -> { throw new FdxException("callback"); });
                pending.add(task);
            }
            return FdxFuture.pending();
        });
        AssetHandle<Asset> handle = manager.load(descriptor("pending"));
        assertThrows(FdxException.class, manager::dispose);
        assertTrue(handle.future().isFailed());
        for (FdxFuture<Asset> task : pending) {
            assertTrue(task.isFailed());
        }
    }

    private void register(BiFunction<AssetLoadContext, AssetDescriptor<Asset>, FdxFuture<Asset>> action) {
        manager.registerLoader(Asset.class, new AssetLoader<Asset>() {
            @Override public Class<Asset> type() { return Asset.class; }
            @Override public FdxFuture<Asset> load(AssetLoadContext context, AssetDescriptor<Asset> descriptor) {
                return action.apply(context, descriptor);
            }
        });
    }

    private void drainOneStepAtATime() {
        for (int i = 0; i < 200; i++) {
            if (manager.update(1, Long.MAX_VALUE)) { return; }
            assertTrue(manager.lastUpdateTaskCount() <= 1);
        }
        fail("Assets did not settle in 200 single-step updates");
    }

    private AssetDescriptor<Asset> descriptor(String path) {
        return AssetDescriptor.of(path, Asset.class);
    }

    private FileHandle file(String path) {
        return (FileHandle)Proxy.newProxyInstance(FileHandle.class.getClassLoader(), new Class<?>[] {FileHandle.class},
                (proxy, method, args) -> method.getName().equals("readBytes") ? reads.get(path) : path);
    }

    private Asset asset(String name) {
        events.add("create:" + name);
        Asset asset = new Asset(name);
        built.put(name, asset);
        return asset;
    }

    private final class Asset implements Disposable {
        final String name;
        int disposed;
        boolean throwOnDispose;
        Asset(String name) { this.name = name; }
        @Override public void dispose() {
            disposed++;
            events.add("dispose:" + name);
            if (throwOnDispose) { throw new FdxException("dispose:" + name); }
        }
        @Override public boolean isDisposed() { return disposed > 0; }
    }
}
