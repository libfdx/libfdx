package io.github.libfdx.tools.uipreview;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

final class PreviewSourceEventsTest {
    @Test
    void watchesSourceEditsAndNewDirectoriesWithoutTreatingBuildOutputsAsSources() throws Exception {
        Path parent = Path.of("build/tmp/preview-events").toAbsolutePath();
        Files.createDirectories(parent);
        Path directory = Files.createTempDirectory(parent, "case-");
        Path source = directory.resolve("src/main/java");
        Files.createDirectories(source);
        Path gradle = directory.resolve("build.gradle");
        Files.writeString(gradle, "// build inputs");
        List<String> roots = List.of(source.toString(), gradle.toString());
        try (var events = new PreviewSourceEvents(roots)) {
            Path ignored = directory.resolve("build/generated/Ignore.java");
            Files.createDirectories(ignored.getParent());
            Files.writeString(ignored, "// ignored output");
            Thread.sleep(100);
            assertFalse(events.changed());
            var before = PreviewSources.scan(roots);
            Path added = source.resolve("new/package/Panel.java");
            Files.createDirectories(added.getParent());
            Files.writeString(added, "class Panel {}");
            await(events);
            var after = PreviewSources.scan(roots);
            assertEquals(java.util.Set.of(added), PreviewSources.changes(before, after));
            Files.delete(added);
            await(events);
            assertEquals(java.util.Set.of(added), PreviewSources.changes(after, PreviewSources.scan(roots)));
        } finally {
            PreviewSnapshot.deleteOwned(parent, directory);
        }
    }

    private void await(PreviewSourceEvents events) throws Exception {
        long deadline = System.nanoTime() + 2_000_000_000L;
        while (System.nanoTime() < deadline) {
            if (events.changed()) return;
            Thread.sleep(10);
        }
        fail("No source notification arrived");
    }

}
