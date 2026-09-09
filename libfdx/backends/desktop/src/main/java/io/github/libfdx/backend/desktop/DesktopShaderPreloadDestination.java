package io.github.libfdx.backend.desktop;

import io.github.libfdx.core.Disposable;
import io.github.libfdx.core.FdxException;
import io.github.libfdx.core.FdxFuture;
import io.github.libfdx.graphics.shader.runtime.ShaderPreloadCapture;
import io.github.libfdx.graphics.shader.runtime.ShaderPreloadExport;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;

/** Desktop asynchronous encoding/file export. Owns one bounded worker; dispose after requested
 * export futures finish. Disposal rejects new work without joining the application thread. Files
 * are shader-preload.json and shader-preload.md in the supplied application-owned directory. */
public final class DesktopShaderPreloadDestination implements ShaderPreloadCapture.Destination, Disposable {
    private final Path directory;
    private final DesktopAssetExecutor executor = new DesktopAssetExecutor(1, 8);

    public DesktopShaderPreloadDestination(Path directory) {
        this.directory = Objects.requireNonNull(directory).toAbsolutePath().normalize();
    }

    @Override public FdxFuture<Void> writeAsync(ShaderPreloadExport snapshot) {
        Objects.requireNonNull(snapshot);
        FdxFuture<Void> result = FdxFuture.pending();
        try {
            if (!executor.submit(() -> {
                try {
                    String manifest = snapshot.manifest().toJson(), report = snapshot.markdown();
                    Files.createDirectories(directory);
                    replace(directory.resolve("shader-preload.json"), manifest);
                    replace(directory.resolve("shader-preload.md"), report);
                    result.complete(null);
                } catch (Throwable failure) { result.completeExceptionally(failure); }
            })) result.completeExceptionally(new FdxException("Shader export worker queue is full"));
        } catch (RuntimeException failure) { result.completeExceptionally(failure); }
        return result;
    }

    private static void replace(Path destination, String text) throws IOException {
        Path temporary = Files.createTempFile(destination.getParent(), ".shader-export-", ".tmp");
        try {
            Files.writeString(temporary, text);
            try { Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
            catch (AtomicMoveNotSupportedException unavailable) { Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temporary); }
    }
    @Override public void dispose() { executor.dispose(); }
    @Override public boolean isDisposed() { return executor.isDisposed(); }
}
