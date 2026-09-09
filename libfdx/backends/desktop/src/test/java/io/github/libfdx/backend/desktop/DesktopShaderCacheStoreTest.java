package io.github.libfdx.backend.desktop;

import io.github.libfdx.core.FdxFuture;
import io.github.libfdx.graphics.shader.runtime.ShaderArtifactCache;
import io.github.libfdx.graphics.shader.runtime.ShaderCacheKey;
import io.github.libfdx.graphics.shader.runtime.ShaderCacheLayer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.channels.FileChannel;
import java.nio.file.StandardOpenOption;
import java.net.URLClassLoader;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import static org.junit.jupiter.api.Assertions.*;

@Timeout(15)
final class DesktopShaderCacheStoreTest {
    @Test void freshStoreReadsAtomicallyWrittenArtifactsAndRejectsPaths() throws Exception {
        Path directory = directory();
        ShaderCacheKey key = ShaderCacheKey.of(ShaderCacheLayer.DXIL, "test-input");
        DesktopShaderCacheStore first = new DesktopShaderCacheStore(directory, 32 * 1024 * 1024);
        try {
            assertThrows(IllegalArgumentException.class, () -> first.readAsync("../escape"));
            assertNull(await(first.readAsync(key.digest())));
            assertTrue(await(new ShaderArtifactCache(first).writeAsync(key, new byte[]{1, 2, 3})));
        } finally { first.dispose(); }
        DesktopShaderCacheStore second = new DesktopShaderCacheStore(directory, 32 * 1024 * 1024);
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
        Files.writeString(unrelated, "preserve");
        DesktopShaderCacheStore store = new DesktopShaderCacheStore(directory, ShaderArtifactCache.MAX_RECORD_BYTES);
        String first = ShaderCacheKey.of(ShaderCacheLayer.SOURCE, "first").digest();
        String second = ShaderCacheKey.of(ShaderCacheLayer.SOURCE, "second").digest();
        try {
            byte[] bytes = new byte[9 * 1024 * 1024];
            Arrays.fill(bytes, (byte)7);
            await(store.writeAsync(first, bytes));
            await(store.writeAsync(second, bytes));
            assertNull(await(store.readAsync(first)));
            assertArrayEquals(bytes, await(store.readAsync(second)));
            assertEquals("preserve", Files.readString(unrelated));
        } finally { store.dispose(); }
    }

    @Test void unavailableDirectoryBecomesAMissWithoutFailingTheCacheClient() throws Exception {
        Path path = directory().resolve("file");
        Files.writeString(path, "occupied");
        DesktopShaderCacheStore store = new DesktopShaderCacheStore(path, 32 * 1024 * 1024);
        try {
            ShaderArtifactCache cache = new ShaderArtifactCache(store);
            ShaderCacheKey key = ShaderCacheKey.of(ShaderCacheLayer.SOURCE, "input");
            assertFalse(await(cache.writeAsync(key, new byte[]{1})));
            assertEquals(1, cache.metrics(key.layer()).storageFailures());
        } finally { store.dispose(); }
    }

    @Test void independentStoresSerializeReadMergeWriteAndDisposalDrainsAcceptedUpdates() throws Exception {
        Path directory = directory();
        DesktopShaderCacheStore first = new DesktopShaderCacheStore(directory, 32 * 1024 * 1024);
        DesktopShaderCacheStore second = new DesktopShaderCacheStore(directory, 32 * 1024 * 1024);
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
        DesktopShaderCacheStore first = new DesktopShaderCacheStore(directory, 32 * 1024 * 1024);
        DesktopShaderCacheStore second = new DesktopShaderCacheStore(directory, 32 * 1024 * 1024);
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

    @Test void lockTimeoutDoesNotInvokeTheUpdaterOrReplaceAnExistingRecord() throws Exception {
        Path directory = directory();
        DesktopShaderCacheStore store = new DesktopShaderCacheStore(directory, 32 * 1024 * 1024);
        String key = processKey();
        try {
            await(store.writeAsync(key, new byte[]{7}));
            try (var channel = FileChannel.open(directory.resolve(".shader-cache.lock"),
                    StandardOpenOption.CREATE, StandardOpenOption.WRITE);
                 var lock = channel.lock()) {
                var pending = store.updateAsync(key, previous -> { fail("Busy transaction cannot run"); return previous; });
                assertThrows(RuntimeException.class, () -> await(pending));
                assertArrayEquals(new byte[]{7}, await(store.readAsync(key)));
            }
            await(store.updateAsync(key, previous -> new byte[]{9}));
            assertArrayEquals(new byte[]{9}, await(store.readAsync(key)));
        } finally { store.dispose(); }
    }

    @Test void separateProcessesPreserveBothWritersUnderContention() throws Exception {
        Path directory = directory().toAbsolutePath();
        Process first = writer(directory, 1), second = writer(directory, 2);
        try {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(8);
            while ((!Files.exists(directory.resolve("ready-1")) || !Files.exists(directory.resolve("ready-2")))
                    && System.nanoTime() < deadline && first.isAlive() && second.isAlive()) Thread.sleep(10);
            assertTrue(Files.exists(directory.resolve("ready-1")), "First cache writer did not start");
            assertTrue(Files.exists(directory.resolve("ready-2")), "Second cache writer did not start");
            Files.writeString(directory.resolve("go"), "go");
            assertTrue(first.waitFor(5, TimeUnit.SECONDS)); assertTrue(second.waitFor(5, TimeUnit.SECONDS));
            assertEquals(0, first.exitValue(), Files.readString(directory.resolve("writer-1.log")));
            assertEquals(0, second.exitValue(), Files.readString(directory.resolve("writer-2.log")));
            DesktopShaderCacheStore store = new DesktopShaderCacheStore(directory, 32 * 1024 * 1024);
            try { assertArrayEquals(new byte[]{3}, await(store.readAsync(processKey()))); }
            finally { store.dispose(); }
        } finally { first.destroyForcibly(); second.destroyForcibly(); }
    }

    private static String processKey() { return ShaderCacheKey.of(ShaderCacheLayer.DRIVER_PIPELINE, "process-merge").digest(); }

    private static Process writer(Path directory, int bit) throws Exception {
        ArrayList<String> paths = new ArrayList<>();
        paths.add(System.getProperty("java.class.path"));
        for (ClassLoader loader = DesktopShaderCacheStoreTest.class.getClassLoader(); loader != null; loader = loader.getParent()) {
            if (loader instanceof URLClassLoader urls) {
                for (var url : urls.getURLs()) if (url.getProtocol().equals("file")) paths.add(Path.of(url.toURI()).toString());
            }
        }
        return new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-cp", String.join(File.pathSeparator, paths), Writer.class.getName(), directory.toString(), Integer.toString(bit))
                .redirectErrorStream(true).redirectOutput(directory.resolve("writer-" + bit + ".log").toFile()).start();
    }

    public static final class Writer {
        public static void main(String[] args) throws Exception {
            Path directory = Path.of(args[0]); int bit = Integer.parseInt(args[1]);
            DesktopShaderCacheStore store = new DesktopShaderCacheStore(directory, 32 * 1024 * 1024);
            try {
                Files.writeString(directory.resolve("ready-" + bit), "ready");
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
                while (!Files.exists(directory.resolve("go"))) {
                    if (System.nanoTime() > deadline) throw new IllegalStateException("Start barrier timed out");
                    Thread.sleep(10);
                }
                for (int i = 0; i < 8; i++) await(store.updateAsync(processKey(), old -> {
                    try { Thread.sleep(10); }
                    catch (InterruptedException failure) { throw new IllegalStateException(failure); }
                    return new byte[]{(byte)((old == null ? 0 : old[0]) | bit)};
                }));
            } finally { store.dispose(); }
        }
    }

    private static Path directory() throws Exception {
        Path build = Path.of("build");
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
