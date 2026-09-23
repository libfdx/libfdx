package io.github.libfdx.backend.android;

import io.github.libfdx.core.Disposable;
import io.github.libfdx.core.FdxException;
import io.github.libfdx.core.FdxFuture;
import io.github.libfdx.core.FdxTask;
import io.github.libfdx.graphics.shader.runtime.ShaderArtifactCache;
import io.github.libfdx.graphics.shader.runtime.ShaderCacheStore;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Objects;
import java.util.concurrent.locks.LockSupport;
import java.util.function.UnaryOperator;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Bounded application-owned cache for an app-private Android directory (for example, a child of Context.getCacheDir()). The supplied directory must be private to this cache;
 * only digest-named .fdxs entries are managed. I/O runs on one bounded worker, never the caller.
 * Successful writes use atomic replacement. Unsupported atomic moves, unavailable storage,
 * queue saturation and cross-process lock timeouts fail the operation, allowing preparation
 * to compile normally. Disposal rejects new operations while accepted operations drain, without
 * joining. Atomic updates hold the same lock across read/update/replacement; lock acquisition
 * waits at most two seconds on the storage worker. No directory access occurs in the constructor.
 */
public final class AndroidShaderCacheStore implements ShaderCacheStore, Disposable {
    private final Path directory;
    private final long maxBytes;
    private final ThreadPoolExecutor executor = new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(256), task -> {
                Thread thread = new Thread(task, "libfdx-shader-cache-io");
                thread.setDaemon(true);
                return thread;
            }, new ThreadPoolExecutor.AbortPolicy());

    public AndroidShaderCacheStore(Path directory, long maxBytes) {
        this.directory = Objects.requireNonNull(directory).toAbsolutePath().normalize();
        if (maxBytes < ShaderArtifactCache.MAX_RECORD_BYTES) {
            throw new IllegalArgumentException("Cache budget must hold at least one maximum-sized record");
        }
        this.maxBytes = maxBytes;
    }

    @Override
    public FdxFuture<byte[]> readAsync(String key) {
        Path entry = entry(key);
        return submit(() -> readEntry(entry));
    }

    private byte[] readEntry(Path entry) throws IOException {
        if (!Files.exists(entry, LinkOption.NOFOLLOW_LINKS)) return null;
        try (FileChannel channel = FileChannel.open(entry, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS)) {
            long size = channel.size();
            if (size < 1 || size > ShaderArtifactCache.MAX_RECORD_BYTES) {
                // A bounded invalid record lets the envelope report corruption rather than I/O failure.
                return new byte[0];
            }
            byte[] bytes = new byte[(int)size];
            ByteBuffer output = ByteBuffer.wrap(bytes);
            while (output.hasRemaining()) if (channel.read(output) < 0) return new byte[0];
            if (channel.size() != size) return new byte[0];
            return bytes;
        }
    }

    @Override
    public FdxFuture<Void> writeAsync(String key, byte[] bytes) {
        Path entry = entry(key);
        if (bytes == null || bytes.length < 1 || bytes.length > ShaderArtifactCache.MAX_RECORD_BYTES) {
            throw new IllegalArgumentException("Shader cache record size is out of bounds");
        }
        return submit(() -> withWriteLock(() -> {
            replace(entry, key, bytes);
            return null;
        }));
    }

    @Override
    public boolean supportsAtomicUpdate() { return true; }

    @Override
    public FdxFuture<Void> updateAsync(String key, UnaryOperator<byte[]> update) {
        Path entry = entry(key);
        Objects.requireNonNull(update, "update");
        return submit(() -> withWriteLock(() -> {
            replace(entry, key, update.apply(readEntry(entry)));
            return null;
        }));
    }

    private void replace(Path entry, String key, byte[] bytes) throws IOException {
        if (bytes == null || bytes.length < 1 || bytes.length > ShaderArtifactCache.MAX_RECORD_BYTES)
            throw new IllegalArgumentException("Shader cache record size is out of bounds");
        trim(entry, bytes.length);
        Path temporary = Files.createTempFile(directory, key + ".", ".tmp");
        try {
            try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.WRITE)) {
                ByteBuffer input = ByteBuffer.wrap(bytes);
                while (input.hasRemaining()) channel.write(input);
                channel.force(true);
            }
            Files.move(temporary, entry, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally { Files.deleteIfExists(temporary); }
    }

    private <T> T withWriteLock(FdxTask<T> work) throws Exception {
        Files.createDirectories(directory);
        try (FileChannel channel = FileChannel.open(directory.resolve(".shader-cache.lock"),
                StandardOpenOption.CREATE, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS);
             FileLock lock = acquireLock(channel)) {
            return work.run();
        }
    }

    private static FileLock acquireLock(FileChannel channel) throws IOException {
        long deadline = System.nanoTime() + 2_000_000_000L;
        do {
            try {
                FileLock lock = channel.tryLock();
                if (lock != null) return lock;
            } catch (OverlappingFileLockException busyInThisProcess) {
                // Another independent store in this JVM holds the same OS lock.
            }
            if (Thread.currentThread().isInterrupted()) throw new IOException("Shader cache lock interrupted");
            if (System.nanoTime() >= deadline) throw new IOException("Shader cache write lock timed out");
            LockSupport.parkNanos(10_000_000L);
        } while (true);
    }

    @Override
    public FdxFuture<Void> removeAsync(String key) {
        Path entry = entry(key);
        return submit(() -> {
            if (!Files.exists(directory, LinkOption.NOFOLLOW_LINKS)) return null;
            return withWriteLock(() -> { Files.deleteIfExists(entry); return null; });
        });
    }

    /** Completes after previously submitted I/O. Stop producers first to use this as a final drain. */
    public FdxFuture<Void> flushAsync() { return submit(() -> null); }

    private void trim(Path replacing, int incomingBytes) throws IOException {
        record Stored(Path path, long size, long modified) { }
        ArrayList<Stored> entries = new ArrayList<>();
        long total = incomingBytes;
        try (var paths = Files.newDirectoryStream(directory, "*.fdxs")) {
            for (Path path : paths) {
                String name = path.getFileName().toString();
                if (path.equals(replacing) || name.length() != 69 || !validDigest(name.substring(0, 64))
                        || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) continue;
                long size = Files.size(path);
                total += size;
                entries.add(new Stored(path, size, Files.getLastModifiedTime(path, LinkOption.NOFOLLOW_LINKS).toMillis()));
            }
        }
        entries.sort(Comparator.comparingLong(Stored::modified));
        for (Stored entry : entries) {
            if (total <= maxBytes) break;
            Files.deleteIfExists(entry.path());
            total -= entry.size();
        }
    }

    private Path entry(String key) {
        if (!validDigest(key)) throw new IllegalArgumentException("Shader cache keys must be lowercase SHA-256 digests");
        return directory.resolve(key + ".fdxs");
    }

    private static boolean validDigest(String key) {
        if (key == null || key.length() != 64) return false;
        for (int i = 0; i < key.length(); i++) {
            char c = key.charAt(i);
            if (!(c >= '0' && c <= '9') && !(c >= 'a' && c <= 'f')) return false;
        }
        return true;
    }

    private <T> FdxFuture<T> submit(FdxTask<T> task) {
        FdxFuture<T> future = FdxFuture.pending();
        try {
            executor.execute(() -> {
                T value;
                try { value = task.run(); }
                catch (Throwable failure) { future.completeExceptionally(failure); return; }
                future.complete(value);
            });
        } catch (RuntimeException failure) { future.completeExceptionally(failure); }
        return future;
    }

    @Override
    public void dispose() { executor.shutdown(); }
    @Override
    public boolean isDisposed() { return executor.isShutdown(); }
}
