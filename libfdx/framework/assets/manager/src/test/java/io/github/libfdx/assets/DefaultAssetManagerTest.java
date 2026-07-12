package io.github.libfdx.assets;

import com.sun.management.ThreadMXBean;
import io.github.libfdx.core.Disposable;
import io.github.libfdx.core.FdxFuture;
import io.github.libfdx.files.FileSystem;
import org.junit.jupiter.api.Test;

import java.lang.management.ManagementFactory;
import java.lang.reflect.Proxy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

final class DefaultAssetManagerTest {
    @Test
    void handleListTracksDuplicateUnloadReloadAndWarmedUpdates() {
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

        for (int i = 0; i < 10_000; i++) {
            manager.update();
        }
        java.lang.management.ThreadMXBean platformBean = ManagementFactory.getThreadMXBean();
        assumeTrue(platformBean instanceof ThreadMXBean);
        ThreadMXBean bean = (ThreadMXBean)platformBean;
        assumeTrue(bean.isThreadAllocatedMemorySupported());
        if (!bean.isThreadAllocatedMemoryEnabled()) {
            bean.setThreadAllocatedMemoryEnabled(true);
        }
        long threadId = Thread.currentThread().threadId();
        long before = bean.getThreadAllocatedBytes(threadId);
        int completed = 0;
        for (int i = 0; i < 2_000; i++) {
            if (manager.update()) {
                completed++;
            }
        }
        long allocated = bean.getThreadAllocatedBytes(threadId) - before;
        assertEquals(2_000, completed);
        assertTrue(allocated <= 512L, "Expected no post-warm-up asset update iterator churn, allocated "
                + allocated + " bytes");

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
