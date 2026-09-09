package io.github.libfdx.backend.android;

import io.github.libfdx.core.FdxFuture;
import io.github.libfdx.graphics.shader.runtime.ShaderArtifactCache;
import io.github.libfdx.graphics.shader.runtime.ShaderCacheKey;
import io.github.libfdx.graphics.shader.runtime.ShaderCacheLayer;
import java.nio.file.Files;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import static org.junit.jupiter.api.Assertions.*;

@Timeout(15)
final class AndroidShaderCacheStoreTest {
    @Test void freshStoreReadsAtomicallyWrittenArtifactsAndRejectsPaths() throws Exception {
        Path directory = directory();
        ShaderCacheKey key = ShaderCacheKey.of(ShaderCacheLayer.SPIRV, "test-input");
        AndroidShaderCacheStore first = new AndroidShaderCacheStore(directory, 32 * 1024 * 1024);
        try {
            assertThrows(IllegalArgumentException.class, () -> first.readAsync("../escape"));
            assertNull(await(first.readAsync(key.digest())));
            assertTrue(await(new ShaderArtifactCache(first).writeAsync(key, new byte[]{1, 2, 3})));
        } finally { first.dispose(); }
        AndroidShaderCacheStore second = new AndroidShaderCacheStore(directory, 32 * 1024 * 1024);
        try {
            ShaderArtifactCache cache = new ShaderArtifactCache(second);
            assertArrayEquals(new byte[]{1, 2, 3}, await(cache.readAsync(key)));
            assertEquals(1, cache.metrics(key.layer()).hits());
            Files.write(directory.resolve(key.digest() + ".fdxs"), new byte[]{0});
            assertNull(await(cache.readAsync(key)));
            assertEquals(1, cache.metrics(key.layer()).invalidEntries());
        } finally { second.dispose(); }
        assertTrue(second.readAsync(key.digest()).isFailed());
    }

    @Test void storageBudgetEvictsOldEntriesWithoutTouchingOtherFiles() throws Exception {
        Path directory = directory();
        Path unrelated = directory.resolve("game-data");
        Files.write(unrelated, "preserve".getBytes(StandardCharsets.UTF_8));
        AndroidShaderCacheStore store = new AndroidShaderCacheStore(directory, ShaderArtifactCache.MAX_RECORD_BYTES);
        String first = ShaderCacheKey.of(ShaderCacheLayer.SOURCE, "first").digest();
        String second = ShaderCacheKey.of(ShaderCacheLayer.SOURCE, "second").digest();
        try {
            byte[] bytes = new byte[9 * 1024 * 1024];
            Arrays.fill(bytes, (byte)7);
            await(store.writeAsync(first, bytes));
            await(store.writeAsync(second, bytes));
            assertNull(await(store.readAsync(first)));
            assertArrayEquals(bytes, await(store.readAsync(second)));
            assertEquals("preserve", new String(Files.readAllBytes(unrelated), StandardCharsets.UTF_8));
        } finally { store.dispose(); }
    }

    @Test void unavailableDirectoryBecomesAMissWithoutFailingTheCacheClient() throws Exception {
        Path path = directory().resolve("file");
        Files.write(path, "occupied".getBytes(StandardCharsets.UTF_8));
        AndroidShaderCacheStore store = new AndroidShaderCacheStore(path, 32 * 1024 * 1024);
        try {
            ShaderArtifactCache cache = new ShaderArtifactCache(store);
            ShaderCacheKey key = ShaderCacheKey.of(ShaderCacheLayer.SOURCE, "input");
            assertFalse(await(cache.writeAsync(key, new byte[]{1})));
            assertEquals(1, cache.metrics(key.layer()).storageFailures());
        } finally { store.dispose(); }
    }

    @Test void independentStoresSerializeReadMergeWriteAndDisposalDrainsAcceptedUpdates() throws Exception {
        Path directory = directory();
        AndroidShaderCacheStore first = new AndroidShaderCacheStore(directory, 32 * 1024 * 1024);
        AndroidShaderCacheStore second = new AndroidShaderCacheStore(directory, 32 * 1024 * 1024);
        String key = ShaderCacheKey.of(ShaderCacheLayer.DRIVER_PIPELINE, "aggregate").digest();
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        Thread caller = Thread.currentThread();
        try {
            var a = first.updateAsync(key, old -> {
                assertNotSame(caller, Thread.currentThread());
                entered.countDown();
                try { assertTrue(release.await(5, TimeUnit.SECONDS)); }
                catch (InterruptedException failure) { throw new AssertionError(failure); }
                assertNull(old); return new byte[]{1};
            });
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            var b = second.updateAsync(key, old -> new byte[]{(byte)(old[0] | 2)});
            assertFalse(b.isDone());
            first.dispose();
            assertTrue(first.isDisposed());
            release.countDown();
            await(a); await(b);
            assertArrayEquals(new byte[]{3}, await(second.readAsync(key)));
            assertTrue(first.updateAsync(key, old -> new byte[]{0}).isFailed());
            assertThrows(RuntimeException.class, () -> await(second.updateAsync(key, old -> {
                throw new IllegalStateException("merge failed");
            })));
            assertArrayEquals(new byte[]{3}, await(second.readAsync(key)));
        } finally { release.countDown(); first.dispose(); second.dispose(); }
    }

    @Test void aggregateMergePreservesNewerDataFromAnIndependentWriter() throws Exception {
        Path directory = directory();
        AndroidShaderCacheStore first = new AndroidShaderCacheStore(directory, 32 * 1024 * 1024);
        AndroidShaderCacheStore second = new AndroidShaderCacheStore(directory, 32 * 1024 * 1024);
        ShaderCacheKey key = ShaderCacheKey.of(ShaderCacheLayer.DRIVER_PIPELINE, "stale-snapshots");
        try {
            ShaderArtifactCache a = new ShaderArtifactCache(first), b = new ShaderArtifactCache(second);
            var one = a.mergeAsync(key, new byte[]{1}, (old, incoming) ->
                    new byte[]{(byte)((old == null ? 0 : old[0]) | incoming[0])});
            var two = b.mergeAsync(key, new byte[]{2}, (old, incoming) ->
                    new byte[]{(byte)((old == null ? 0 : old[0]) | incoming[0])});
            assertTrue(await(one)); assertTrue(await(two));
            assertTrue(await(a.mergeAsync(key, new byte[]{1}, (old, incoming) -> new byte[]{(byte)(old[0] | incoming[0])})));
            assertArrayEquals(new byte[]{3}, await(b.readAsync(key)));
            Files.write(directory.resolve(key.digest() + ".fdxs"), new byte[]{0});
            assertTrue(await(b.mergeAsync(key, new byte[]{4}, (old, incoming) -> {
                assertNull(old); return incoming;
            })));
            assertArrayEquals(new byte[]{4}, await(a.readAsync(key)));
        } finally { first.dispose(); second.dispose(); }
    }

    private static Path directory() throws Exception {
        Path build = Paths.get("build");
        Files.createDirectories(build);
        return Files.createTempDirectory(build, "shader-cache-test-");
    }

    private static <T> T await(FdxFuture<T> future) throws Exception {
        CountDownLatch done = new CountDownLatch(1);
        future.onSuccess(value -> done.countDown()).onFailure(failure -> done.countDown());
        assertTrue(done.await(10, TimeUnit.SECONDS), "Cache I/O did not complete");
        return future.get();
    }
}
