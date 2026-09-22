package io.github.libfdx.assets;

import io.github.libfdx.core.Disposable;
import io.github.libfdx.core.FdxFuture;
import io.github.libfdx.files.FileSystem;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class DefaultAssetManagerTest {
    @Test
    void updateReportsCompletionRatherThanSuccessfulLoading() {
        DefaultAssetManager manager = new DefaultAssetManager(files());
        assertTrue(manager.update());

        FdxFuture<TestAsset> pending = FdxFuture.pending();
        manager.registerLoader(TestAsset.class, loader(pending));
        AssetHandle<TestAsset> handle = manager.load(AssetDescriptor.of("pending.asset", TestAsset.class));
        assertFalse(manager.update());

        RuntimeException failure = new RuntimeException("Cannot decode asset");
        pending.completeExceptionally(failure);
        assertTrue(manager.update());
        assertEquals(AssetStatus.FAILED, handle.status());
        assertSame(failure, assertThrows(RuntimeException.class, handle.future()::get));
        manager.finishLoading();
        assertSame(failure, assertThrows(RuntimeException.class,
                () -> manager.get("pending.asset", TestAsset.class)).getCause());
        manager.dispose();
    }

    @Test
    void handleListTracksDuplicateUnloadReloadAndRepeatedUpdates() {
        DefaultAssetManager manager = new DefaultAssetManager(files());
        manager.registerLoader(TestAsset.class, new AssetLoader<TestAsset>() {
            @Override
            public Class<TestAsset> type() {
                return TestAsset.class;
            }

            @Override
            public FdxFuture<TestAsset> load(AssetLoadContext context, AssetDescriptor<TestAsset> descriptor) {
                return FdxFuture.completed(new TestAsset());
            }
        });
        AssetDescriptor<TestAsset> descriptor = AssetDescriptor.of("test.asset", TestAsset.class);

        AssetHandle<TestAsset> first = manager.load(descriptor);
        assertSame(first, manager.load(descriptor));
        assertTrue(manager.update());
        assertEquals(AssetStatus.LOADED, first.status());

        for (int i = 0; i < 2_000; i++) {
            assertTrue(manager.update());
        }

        TestAsset firstAsset = first.asset();
        manager.unload(descriptor.path());
        assertEquals(1, firstAsset.disposeCount);
        assertEquals(AssetStatus.UNLOADED, first.status());

        AssetHandle<TestAsset> second = manager.load(descriptor);
        assertNotSame(first, second);
        TestAsset secondAsset = second.asset();
        manager.dispose();
        assertEquals(1, secondAsset.disposeCount);
        assertEquals(AssetStatus.UNLOADED, second.status());
    }

    @Test
    void lateCompletionAfterUnloadIsRejectedAndDisposed() {
        DefaultAssetManager manager = new DefaultAssetManager(files());
        FdxFuture<TestAsset> loaderFuture = FdxFuture.pending();
        manager.registerLoader(TestAsset.class, loader(loaderFuture));
        AssetDescriptor<TestAsset> descriptor = AssetDescriptor.of("pending.asset", TestAsset.class);

        AssetHandle<TestAsset> handle = manager.load(descriptor);
        assertEquals(AssetStatus.LOADING, handle.status());
        assertFalse(handle.future().isDone());

        manager.unload(descriptor.path());

        assertEquals(AssetStatus.UNLOADED, handle.status());
        assertNull(handle.asset());
        assertTrue(handle.future().isFailed());
        assertThrows(RuntimeException.class, handle.future()::get);

        TestAsset lateAsset = new TestAsset();
        loaderFuture.complete(lateAsset);

        assertEquals(1, lateAsset.disposeCount);
        assertEquals(AssetStatus.UNLOADED, handle.status());
        assertNull(handle.asset());
        assertNull(manager.find(descriptor.path(), TestAsset.class));
    }

    @Test
    void lateCompletionAfterManagerDisposeIsRejectedAndDisposed() {
        DefaultAssetManager manager = new DefaultAssetManager(files());
        FdxFuture<TestAsset> loaderFuture = FdxFuture.pending();
        manager.registerLoader(TestAsset.class, loader(loaderFuture));
        AssetHandle<TestAsset> handle = manager.load(AssetDescriptor.of("pending.asset", TestAsset.class));

        manager.dispose();

        assertTrue(manager.isDisposed());
        assertEquals(AssetStatus.UNLOADED, handle.status());
        assertTrue(handle.future().isFailed());

        TestAsset lateAsset = new TestAsset();
        loaderFuture.complete(lateAsset);

        assertEquals(1, lateAsset.disposeCount);
        assertEquals(AssetStatus.UNLOADED, handle.status());
        assertNull(handle.asset());
    }

    private static AssetLoader<TestAsset> loader(FdxFuture<TestAsset> future) {
        return new AssetLoader<TestAsset>() {
            @Override
            public Class<TestAsset> type() {
                return TestAsset.class;
            }

            @Override
            public FdxFuture<TestAsset> load(AssetLoadContext context, AssetDescriptor<TestAsset> descriptor) {
                return future;
            }
        };
    }

    private static FileSystem files() {
        return (FileSystem)Proxy.newProxyInstance(FileSystem.class.getClassLoader(),
                new Class<?>[] { FileSystem.class }, (proxy, method, arguments) -> null);
    }

    private static final class TestAsset implements Disposable {
        int disposeCount;

        @Override
        public void dispose() {
            disposeCount++;
        }

        @Override
        public boolean isDisposed() {
            return disposeCount > 0;
        }
    }
}
