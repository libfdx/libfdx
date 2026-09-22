package io.github.libfdx.assets;

import io.github.libfdx.core.Disposable;
import io.github.libfdx.core.FdxException;
import io.github.libfdx.core.FdxFuture;
import io.github.libfdx.files.FileHandle;
import io.github.libfdx.files.FileSystem;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

final class DefaultAssetManagerScopeTest {
    private final Map<String, String[]> graph = new HashMap<>();
    private final Map<String, FdxFuture<byte[]>> reads = new HashMap<>();
    private final Map<String, Integer> starts = new HashMap<>();
    private final Map<String, Asset> built = new HashMap<>();
    private final List<String> disposed = new ArrayList<>();
    private final FileSystem files = (FileSystem)Proxy.newProxyInstance(FileSystem.class.getClassLoader(),
            new Class<?>[] {FileSystem.class}, (proxy, method, args) -> null);
    private final DefaultAssetManager manager = manager();

    private DefaultAssetManager manager() {
        DefaultAssetManager result = new DefaultAssetManager(files);
        result.registerLoader(Asset.class, new AssetLoader<Asset>() {
            @Override public Class<Asset> type() { return Asset.class; }
            @Override public FdxFuture<Asset> load(AssetLoadContext context, AssetDescriptor<Asset> descriptor) {
                String path = descriptor.path();
                starts.merge(path, 1, Integer::sum);
                for (String child : graph.getOrDefault(path, new String[0])) {
                    context.dependency(descriptor(child));
                }
                if (reads.containsKey(path)) {
                    FileHandle file = (FileHandle)Proxy.newProxyInstance(FileHandle.class.getClassLoader(),
                            new Class<?>[] {FileHandle.class}, (proxy, method, args) -> reads.get(path));
                    FdxFuture<Asset> future = FdxFuture.pending();
                    context.readBytes(file).onSuccess(bytes -> context.completeOnUpdate(() -> create(path))
                            .onSuccess(future::complete).onFailure(future::completeExceptionally))
                            .onFailure(future::completeExceptionally);
                    return future;
                }
                return context.completeOnUpdate(() -> create(path));
            }
        });
        return result;
    }

    @AfterEach
    void closeManager() { manager.dispose(); }

    @Test
    void twoScopesShareAnAssetAndOnlyFinalReleaseDisposesItsDependencyGraph() {
        graph.put("parent", new String[] {"child"});
        AssetScope a = manager.createScope();
        AssetScope b = manager.createScope();
        AssetLease<Asset> first = a.load(descriptor("parent"));
        AssetLease<Asset> second = b.load(descriptor("parent"));
        assertNotSame(first, second);
        drain();
        Asset parent = first.asset();
        assertSame(parent, second.asset());
        assertEquals(1, starts.get("parent"));
        assertEquals(1, starts.get("child"));
        a.dispose();
        assertTrue(a.isDisposed());
        assertTrue(first.isDisposed());
        assertEquals(AssetStatus.UNLOADED, first.status());
        assertNull(first.asset());
        assertSame(parent, first.future().get()); // A completed future is not a lease.
        assertSame(parent, second.asset());
        assertTrue(disposed.isEmpty());
        b.dispose();
        assertEquals(List.of("parent", "child"), disposed);
        assertNull(manager.find("parent", Asset.class));
        assertNull(manager.find("child", Asset.class));
        a.dispose();
        b.dispose();
        first.dispose();
        second.dispose();
        assertEquals(2, disposed.size());
    }

    @Test
    void duplicateRequestsWithinAScopeAreIndependentlyReleasable() {
        AssetScope scope = manager.createScope();
        AssetLease<Asset> first = scope.load(descriptor("same"));
        AssetLease<Asset> second = scope.load(descriptor("same"));
        first.dispose();
        assertTrue(first.future().isFailed());
        assertFalse(second.future().isDone());
        drain();
        assertTrue(second.isLoaded());
        assertEquals(1, starts.get("same"));
        scope.dispose();
        assertEquals(List.of("same"), disposed);
    }

    @Test
    void directUnloadDoesNotReleaseScopedOrStandaloneLeases() {
        AssetScope scope = manager.createScope();
        AssetLease<Asset> scoped = scope.load(descriptor("same"));
        AssetLease<Asset> standalone = manager.acquire(descriptor("same"));
        AssetHandle<Asset> direct = manager.load(descriptor("same"));
        drain();
        manager.unload("same");
        assertTrue(direct.isLoaded());
        scope.dispose();
        assertTrue(standalone.isLoaded());
        assertTrue(scoped.isDisposed());
        assertTrue(disposed.isEmpty());
        standalone.dispose();
        assertEquals(AssetStatus.UNLOADED, direct.status());
        assertEquals(List.of("same"), disposed);
    }

    @Test
    void directOwnerSurvivesFinalLeaseRelease() {
        AssetHandle<Asset> direct = manager.load(descriptor("same"));
        AssetLease<Asset> lease = manager.acquire(descriptor("same"));
        drain();
        lease.dispose();
        assertTrue(direct.isLoaded());
        assertTrue(disposed.isEmpty());
        manager.unload("same");
        assertEquals(List.of("same"), disposed);
    }

    @Test
    void closingOnePendingScopeCancelsOnlyItsFutureAndDoesNotPinItsParent() {
        reads.put("child", FdxFuture.pending());
        graph.put("A", new String[] {"child"});
        graph.put("B", new String[] {"child"});
        AssetScope a = manager.createScope();
        AssetScope b = manager.createScope();
        AssetLease<Asset> first = a.load(descriptor("A"));
        AssetLease<Asset> second = b.load(descriptor("B"));
        int[] failures = {0};
        first.future().onFailure(error -> failures[0]++);
        assertFalse(manager.update());
        a.dispose();
        assertEquals(1, failures[0]);
        assertFalse(second.future().isDone());
        reads.get("child").complete(new byte[0]);
        drain();
        assertFalse(built.containsKey("A"));
        assertTrue(second.isLoaded());
        b.dispose();
        assertEquals(List.of("B", "child"), disposed);
        assertEquals(1, failures[0]);
    }

    @Test
    void dependencyLeaseOutlivesItsLastParentAndCanReleaseIndependently() {
        graph.put("parent", new String[] {"child"});
        AssetScope level = manager.createScope();
        level.load(descriptor("parent"));
        AssetLease<Asset> child = manager.acquire(descriptor("child"));
        drain();
        level.dispose();
        assertEquals(List.of("parent"), disposed);
        assertTrue(child.isLoaded());
        assertFalse(child.asset().isDisposed());
        child.dispose();
        assertEquals(List.of("parent", "child"), disposed);
    }

    @Test
    void releasedAttemptCannotCompleteALaterLeaseForTheSamePath() {
        FdxFuture<byte[]> oldRead = FdxFuture.pending();
        reads.put("same", oldRead);
        AssetScope oldScope = manager.createScope();
        AssetLease<Asset> old = oldScope.load(descriptor("same"));
        manager.update();
        oldScope.dispose();
        FdxFuture<byte[]> newRead = FdxFuture.pending();
        reads.put("same", newRead);
        AssetLease<Asset> current = manager.acquire(descriptor("same"));
        manager.update();
        oldRead.complete(new byte[0]);
        assertFalse(manager.update());
        assertFalse(current.future().isDone());
        newRead.complete(new byte[0]);
        drain();
        assertTrue(current.isLoaded());
        assertTrue(old.future().isFailed());
        assertEquals(2, starts.get("same"));
        current.dispose();
        assertEquals(List.of("same"), disposed);
    }

    @Test
    void leaseNotificationsIncludingCachedResultsShareTheTaskBudget() {
        List<AssetLease<Asset>> leases = new ArrayList<>();
        int[] callbacks = {0};
        for (int i = 0; i < 12; i++) {
            AssetLease<Asset> lease = manager.acquire(descriptor("same"));
            lease.future().onSuccess(asset -> callbacks[0]++);
            leases.add(lease);
        }
        for (int i = 0; i < 100 && callbacks[0] < 12; i++) {
            int before = callbacks[0];
            manager.update(1, Long.MAX_VALUE);
            assertTrue(callbacks[0] - before <= 1);
        }
        assertEquals(12, callbacks[0]);
        AssetLease<Asset> cached = manager.acquire(descriptor("same"));
        assertFalse(cached.isLoaded());
        assertFalse(cached.future().isDone());
        assertFalse(manager.update(0, 0));
        assertTrue(manager.update(1, Long.MAX_VALUE));
        assertTrue(cached.isLoaded());
        assertEquals(1, starts.get("same"));
    }

    @Test
    void cancellationAfterFinalizationReleasesTheQueuedParentBeforeItsChild() {
        graph.put("parent", new String[] {"child"});
        AssetScope scope = manager.createScope();
        AssetLease<Asset> lease = scope.load(descriptor("parent"));
        for (int i = 0; i < 30 && !built.containsKey("parent"); i++) { manager.update(1, Long.MAX_VALUE); }
        assertNotNull(built.get("parent"));
        assertFalse(lease.future().isDone());
        scope.dispose();
        assertEquals(List.of("parent", "child"), disposed);
        drain();
        assertTrue(lease.future().isFailed());
        assertEquals(2, disposed.size());
    }

    @Test
    void failedDependencyNotifiesAllLeasesOnceAndRetriesAfterAllOwnersRelease() {
        graph.put("parent", new String[] {"child"});
        reads.put("child", FdxFuture.pending());
        AssetScope scope = manager.createScope();
        AssetLease<Asset> first = scope.load(descriptor("parent"));
        AssetLease<Asset> second = manager.acquire(descriptor("parent"));
        int[] failures = {0};
        first.future().onFailure(error -> failures[0]++);
        second.future().onFailure(error -> failures[0]++);
        manager.update();
        reads.get("child").completeExceptionally(new FdxException("bad input"));
        drain();
        assertEquals(2, failures[0]);
        assertEquals(AssetStatus.FAILED, first.status());
        assertTrue(assertThrows(FdxException.class, first.future()::get).getMessage().contains("child"));
        scope.dispose();
        second.dispose();
        reads.put("child", FdxFuture.completed(new byte[0]));
        AssetLease<Asset> retry = manager.acquire(descriptor("parent"));
        drain();
        assertTrue(retry.isLoaded());
        assertEquals(2, starts.get("parent"));
        assertEquals(2, failures[0]);
    }

    @Test
    void scopeCloseInvalidatesEveryLeaseBeforeCallbacksAndContinuesAfterErrors() {
        AssetScope scope = manager.createScope();
        AssetLease<Asset> a = scope.load(descriptor("A"));
        AssetLease<Asset> b = scope.load(descriptor("B"));
        int[] cancelled = {0};
        a.future().onFailure(error -> { cancelled[0]++; throw new FdxException("first callback"); });
        b.future().onFailure(error -> {
            cancelled[0]++;
            assertTrue(a.isDisposed());
            assertNull(a.asset());
            assertThrows(FdxException.class, () -> scope.load(descriptor("closed")));
            throw new FdxException("second callback");
        });
        FdxException failure = assertThrows(FdxException.class, scope::dispose);
        assertEquals(1, failure.getSuppressed().length);
        assertEquals(2, cancelled[0]);
        assertTrue(a.future().isFailed());
        assertTrue(b.future().isFailed());
        drain();
        assertTrue(built.isEmpty());
        scope.dispose();
    }

    @Test
    void throwingDisposersDoNotLeaveOtherLeasesOrResourcesAlive() {
        AssetScope scope = manager.createScope();
        scope.load(descriptor("A"));
        scope.load(descriptor("B"));
        scope.load(descriptor("C"));
        drain();
        built.get("A").throwOnDispose = true;
        built.get("C").throwOnDispose = true;
        assertThrows(FdxException.class, scope::dispose);
        assertEquals(3, disposed.size());
        assertNull(manager.find("A", Asset.class));
        assertNull(manager.find("B", Asset.class));
        assertNull(manager.find("C", Asset.class));
    }

    @Test
    void callbacksCanCloseSiblingLeasesAndTheManagerWithoutCorruptingMembership() {
        AssetScope scope = manager.createScope();
        AssetLease<Asset> first = scope.load(descriptor("same"));
        AssetLease<Asset> second = scope.load(descriptor("same"));
        AssetLease<Asset> other = manager.acquire(descriptor("other"));
        first.future().onSuccess(asset -> {
            first.dispose();
            second.dispose();
            manager.dispose();
        });
        manager.update();
        assertTrue(manager.isDisposed());
        assertTrue(scope.isDisposed());
        assertTrue(first.isDisposed());
        assertTrue(second.future().isFailed());
        assertTrue(other.future().isFailed());
        assertThrows(FdxException.class, manager::createScope);
        assertThrows(FdxException.class, () -> manager.acquire(descriptor("late")));
        assertEquals(1, built.get("same").disposals);
    }

    @Test
    void managerDisposalClosesAllScopesAndStandaloneLeasesIncludingFailedCallbacks() {
        AssetScope a = manager.createScope();
        AssetScope b = manager.createScope();
        AssetLease<Asset> first = a.load(descriptor("same"));
        AssetLease<Asset> second = b.load(descriptor("same"));
        AssetLease<Asset> third = manager.acquire(descriptor("same"));
        first.future().onFailure(error -> { throw new FdxException("cancellation callback"); });
        assertThrows(FdxException.class, manager::dispose);
        assertTrue(a.isDisposed());
        assertTrue(b.isDisposed());
        for (AssetLease<Asset> lease : List.of(first, second, third)) {
            assertTrue(lease.isDisposed());
            assertTrue(lease.future().isFailed());
            lease.dispose();
        }
    }

    @Test
    void independentManagersNeverShareResourcesAtTheSamePath() {
        DefaultAssetManager second = manager();
        try {
            AssetScope firstDomain = manager.createScope();
            AssetScope secondDomain = second.createScope();
            AssetLease<Asset> first = firstDomain.load(descriptor("texture"));
            AssetLease<Asset> other = secondDomain.load(descriptor("texture"));
            manager.finishLoading();
            second.finishLoading();
            assertNotSame(first.asset(), other.asset());
            Asset otherResource = other.asset();
            firstDomain.dispose();
            assertFalse(otherResource.isDisposed());
            assertTrue(other.isLoaded());
            secondDomain.dispose();
            assertEquals(1, otherResource.disposals);
        } finally {
            second.dispose();
        }
    }

    @Test
    void manyTransitionsAndOutOfOrderReleasesLeaveNoAccidentalPins() {
        for (int transition = 0; transition < 100; transition++) {
            AssetScope first = manager.createScope();
            AssetScope middle = manager.createScope();
            AssetScope last = manager.createScope();
            List<AssetLease<Asset>> leases = new ArrayList<>();
            for (int i = 0; i < 12; i++) {
                AssetScope owner = i % 3 == 0 ? first : i % 3 == 1 ? middle : last;
                leases.add(owner.load(descriptor("same")));
            }
            drain();
            leases.get(2).dispose();
            leases.get(7).dispose();
            first.dispose();
            middle.dispose();
            assertTrue(leases.get(11).isLoaded());
            last.dispose();
            assertNull(manager.find("same", Asset.class));
        }
        assertEquals(100, starts.get("same"));
        assertEquals(100, disposed.size());
    }

    @Test
    void ownershipMutationsRejectWorkerThreads() throws Exception {
        AssetScope scope = manager.createScope();
        AssetLease<Asset> lease = scope.load(descriptor("same"));
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread worker = new Thread(() -> {
            try {
                assertThrows(FdxException.class, manager::createScope);
                assertThrows(FdxException.class, () -> manager.acquire(descriptor("wrong")));
                assertThrows(FdxException.class, () -> scope.load(descriptor("wrong")));
                assertThrows(FdxException.class, scope::dispose);
                assertThrows(FdxException.class, lease::dispose);
            } catch (Throwable error) { failure.set(error); }
        });
        worker.start();
        worker.join(3_000);
        assertFalse(worker.isAlive());
        assertNull(failure.get());
        assertFalse(scope.isDisposed());
        drain();
        assertTrue(lease.isLoaded());
    }

    @Test
    void repeatedLeaseReadsAndManagerUpdatesRemainLoaded() {
        AssetLease<Asset> lease = manager.createScope().load(descriptor("same"));
        drain();
        for (int i = 0; i < 2_000; i++) {
            assertNotNull(lease.asset());
            assertTrue(lease.isLoaded());
            assertTrue(manager.update(2, Long.MAX_VALUE));
        }
    }

    private void drain() {
        for (int i = 0; i < 200; i++) {
            if (manager.update(1, Long.MAX_VALUE)) { return; }
        }
        fail("Scoped loading did not settle");
    }

    private Asset create(String path) {
        Asset asset = new Asset(path);
        built.put(path, asset);
        return asset;
    }

    private static AssetDescriptor<Asset> descriptor(String path) { return AssetDescriptor.of(path, Asset.class); }

    private final class Asset implements Disposable {
        final String path;
        int disposals;
        boolean throwOnDispose;
        Asset(String path) { this.path = path; }
        @Override public void dispose() {
            disposals++;
            disposed.add(path);
            if (throwOnDispose) { throw new FdxException("dispose " + path); }
        }
        @Override public boolean isDisposed() { return disposals != 0; }
    }
}
