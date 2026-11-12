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
    static final String SELECTION_FILE_NAME = "entry-selection";

    record EntrySelection(String entryClass, String target) { }

    private PreviewSessionFiles() {
    }

    static Path debugLinesFile() {
        String target = System.getProperty(TARGET_FILE);
        return target == null ? null : Path.of(target).toAbsolutePath().getParent().getParent().resolve("debug-lines");
    }

    static String read(Path workerDirectory) throws IOException {
        Path target = workerDirectory.resolve(FILE_NAME);
        return Files.isRegularFile(target) ? Files.readString(target).strip() : "";
    }

    static Path entrySelectionFile() {
        String target = System.getProperty(TARGET_FILE);
        if (target == null || System.getProperty(PreviewEntries.CATALOG_PROPERTY) == null) return null;
        return Path.of(target).resolveSibling(SELECTION_FILE_NAME);
    }

    static EntrySelection readSelection(Path file, String startupEntry) throws IOException {
        String value = file != null && Files.isRegularFile(file) ? Files.readString(file) : "";
        int separator = value.indexOf('\n');
        if (separator < 0) return new EntrySelection(startupEntry, value);
        return new EntrySelection(value.substring(0, separator), value.substring(separator + 1));
    }

    static void writeSelection(Path file, EntrySelection selection) throws IOException {
        write(file, selection.entryClass() + "\n" + selection.target());
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
