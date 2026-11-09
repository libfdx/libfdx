package io.github.libfdx.tools.uipreview;

import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** Data-only handoff files shared by the watcher, preview workers and status readers. */
final class PreviewSessionFiles {
    static final String TARGET_FILE = "libfdx.uiPreview.targetFile";
    static final String RESTORED_TARGET = "libfdx.uiPreview.restoredTarget";
    static final String FILE_NAME = "active-target";

    private PreviewSessionFiles() {
    }

    static String read(Path workerDirectory) throws IOException {
        Path target = workerDirectory.resolve(FILE_NAME);
        return Files.isRegularFile(target) ? Files.readString(target).strip() : "";
    }

    static void write(Path target, String id) throws IOException {
        Path temporary = target.resolveSibling(target.getFileName() + ".tmp");
        try {
            Files.writeString(temporary, id);
            replace(temporary, target);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    static void replace(Path temporary, Path destination) throws IOException {
        for (int attempt = 0; ; attempt++) {
            try {
                try {
                    Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                } catch (AtomicMoveNotSupportedException ignored) {
                    Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING);
                }
                return;
            } catch (AccessDeniedException denied) {
                // A reader can briefly prevent replacing an open file on Windows.
                if (attempt == 10) throw denied;
                try {
                    Thread.sleep(10);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    denied.addSuppressed(interrupted);
                    throw denied;
                }
            }
        }
    }

}
