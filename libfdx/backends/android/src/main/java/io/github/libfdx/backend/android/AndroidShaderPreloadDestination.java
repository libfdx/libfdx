package io.github.libfdx.backend.android;

import io.github.libfdx.core.Disposable;
import io.github.libfdx.core.FdxFuture;
import io.github.libfdx.graphics.shader.runtime.ShaderPreloadCapture;
import io.github.libfdx.graphics.shader.runtime.ShaderPreloadExport;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** Application-owned export into a dedicated app-private directory. Encodes a consistent
 * capture snapshot and writes shader-preload.json and shader-preload.md on one bounded worker.
 * No encoding or filesystem I/O occurs on the caller. Dispose after export futures finish;
 * disposal rejects new work and drains accepted exports without joining the application thread. */
public final class AndroidShaderPreloadDestination implements ShaderPreloadCapture.Destination, Disposable {
    private final Path directory;
    private final ThreadPoolExecutor executor = new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(8), task -> {
                Thread thread = new Thread(task, "libfdx-shader-export");
                thread.setDaemon(true);
                return thread;
            }, new ThreadPoolExecutor.AbortPolicy());

    public AndroidShaderPreloadDestination(Path directory) {
        this.directory = Objects.requireNonNull(directory).toAbsolutePath().normalize();
    }

    @Override public FdxFuture<Void> writeAsync(ShaderPreloadExport snapshot) {
        Objects.requireNonNull(snapshot);
        FdxFuture<Void> result = FdxFuture.pending();
        try {
            executor.execute(() -> {
                try {
                    String manifest = snapshot.manifest().toJson(), report = snapshot.markdown();
                    Files.createDirectories(directory);
                    replace(directory.resolve("shader-preload.json"), manifest);
                    replace(directory.resolve("shader-preload.md"), report);
                    result.complete(null);
                } catch (Throwable failure) { result.completeExceptionally(failure); }
            });
        } catch (RuntimeException failure) { result.completeExceptionally(failure); }
        return result;
    }

    private static void replace(Path destination, String text) throws IOException {
        Path temporary = Files.createTempFile(destination.getParent(), ".shader-export-", ".tmp");
        try {
            Files.write(temporary, text.getBytes(StandardCharsets.UTF_8));
            Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } finally { Files.deleteIfExists(temporary); }
    }

    @Override public void dispose() { executor.shutdown(); }
    @Override public boolean isDisposed() { return executor.isShutdown(); }
}
