package io.github.libfdx.tools.uipreview;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.FileVisitResult;
import java.nio.file.NoSuchFileException;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** Polls source/asset inputs only, including new/deleted directories. Never scans for Java methods. */
final class PreviewSources {
    static long fingerprint(List<String> roots) throws IOException {
        long result = 0;
        for (String value : roots) {
            Path root = Path.of(value);
            if (!Files.exists(root)) { result += value.hashCode(); continue; }
            if (Files.isRegularFile(root)) { result += file(root); continue; }
            long[] directory = {0};
            Files.walkFileTree(root, new SimpleFileVisitor<>() {
                public FileVisitResult visitFile(Path path, BasicFileAttributes attributes) throws IOException {
                    if (Files.isRegularFile(path)) directory[0] += file(path);
                    return FileVisitResult.CONTINUE;
                }

                public FileVisitResult visitFileFailed(Path path, IOException error) throws IOException {
                    if (!(error instanceof NoSuchFileException)) throw error;
                    return FileVisitResult.CONTINUE; // Editors can replace/delete trees during a poll.
                }

                public FileVisitResult postVisitDirectory(Path path, IOException error) throws IOException {
                    if (error != null && !(error instanceof NoSuchFileException)) throw error;
                    return FileVisitResult.CONTINUE;
                }

            });
            result += directory[0];
        }
        return result;
    }

    private static long file(Path path) throws IOException {
        try {
            BasicFileAttributes attributes = Files.readAttributes(path, BasicFileAttributes.class);
            return ((long)path.toString().hashCode() * 0x9e3779b97f4a7c15L)
                    ^ attributes.lastModifiedTime().to(TimeUnit.NANOSECONDS) ^ (attributes.size() * 0x100000001b3L);
        } catch (java.nio.file.NoSuchFileException removedDuringScan) { return 0; }
    }

}
