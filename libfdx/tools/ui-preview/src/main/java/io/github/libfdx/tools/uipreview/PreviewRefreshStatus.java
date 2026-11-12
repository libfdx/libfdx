package io.github.libfdx.tools.uipreview;

import io.github.libfdx.ui.Ui;
import io.github.libfdx.ui.UiState;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;

/** Small host-owned progress indicator, independent of the composition being reloaded. */
final class PreviewRefreshStatus {
    private final Path file;
    private final UiState<String> text = Ui.state("Save source to refresh");
    private long nextPoll;
    private FileTime updated;

    PreviewRefreshStatus(Path file) {
        this.file = file;
    }

    String text() {
        return text.get();
    }

    void update(long now) {
        if (file == null || now < nextPoll) return;
        nextPoll = now + 150_000_000L;
        try {
            if (!Files.isRegularFile(file)) return;
            FileTime modified = Files.getLastModifiedTime(file);
            if (modified.equals(updated)) return;
            updated = modified;
            var status = PreviewReloadRequest.readValues(file);
            String message = switch (status.getProperty("state", "")) {
                case "compiling" -> "Compiling changes...";
                case "building", "queued" -> "Building project...";
                case "reloading", "starting" -> "Updating preview...";
                case "build-failed" -> "Compile error: see build.log";
                case "runtime-failed", "target-unavailable" -> "Update failed: see preview.log";
                case "running" -> Long.parseLong(status.getProperty("refresh", "0")) > 0
                        ? "Updated in " + status.getProperty("refreshMillis", "0") + " ms" : "Save source to refresh";
                default -> "Save source to refresh";
            };
            text.set(message);
        } catch (IOException | IllegalArgumentException ignored) {
            // Keep the last status while the controller replaces its metadata or exits.
        }
    }

}
