package io.github.libfdx.tools.uipreview;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.Properties;
import org.junit.jupiter.api.Test;

final class PreviewRefreshStatusTest {
    @Test
    void reportsProgressErrorsAndFirstFrameTimingWithoutReplacingTheCanvas() throws Exception {
        Path parent = Path.of("build/tmp/preview-status").toAbsolutePath();
        Files.createDirectories(parent);
        Path directory = Files.createTempDirectory(parent, "case-");
        Path file = directory.resolve("status.properties");
        PreviewRefreshStatus status = new PreviewRefreshStatus(file);
        try {
            assertEquals("Save source to refresh", status.text());
            write(file, "compiling", 1);
            status.update(1_000_000_000L);
            assertEquals("Compiling changes...", status.text());
            write(file, "build-failed", 2);
            status.update(2_000_000_000L);
            assertEquals("Compile error: see build.log", status.text());
            write(file, "running", 3);
            status.update(3_000_000_000L);
            assertEquals("Updated in 345 ms", status.text());
            Files.delete(file);
            status.update(4_000_000_000L);
            assertEquals("Updated in 345 ms", status.text());
        } finally {
            PreviewSnapshot.deleteOwned(parent, directory);
        }
    }

    private void write(Path file, String state, int version) throws Exception {
        Properties values = new Properties();
        values.setProperty("state", state);
        values.setProperty("refresh", "1");
        values.setProperty("refreshMillis", "345");
        try (var writer = Files.newBufferedWriter(file)) { values.store(writer, null); }
        Files.setLastModifiedTime(file, FileTime.fromMillis(version * 1_000L));
    }

}
