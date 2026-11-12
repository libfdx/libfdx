package io.github.libfdx.tools.uipreview;

import io.github.libfdx.ui.Ui;
import io.github.libfdx.ui.UiBooleanState;
import io.github.libfdx.ui.UiRoot;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Owns the host's bounds overlay and its optional session preference. */
final class PreviewDebugLines {
    private final Path file;
    private final UiBooleanState enabled;
    private boolean saved;

    PreviewDebugLines(Path file) {
        this.file = file == null ? null : file.toAbsolutePath();
        try {
            saved = file != null && Files.isRegularFile(file) && Boolean.parseBoolean(Files.readString(file).strip());
        } catch (IOException error) {
            System.err.println("[ui-preview] Cannot read debug preference: " + error);
        }
        enabled = Ui.state(saved);
    }

    UiBooleanState state() {
        return enabled;
    }

    void save() {
        boolean value = enabled.get();
        if (value == saved) return;
        if (file != null) {
            try {
                write(value);
            } catch (IOException error) {
                System.err.println("[ui-preview] Cannot save debug preference: " + error);
            }
        }
        saved = value;
    }

    private void write(boolean value) throws IOException {
        Path temporary = Files.createTempFile(file.getParent(), "debug-lines-", ".tmp");
        try {
            Files.writeString(temporary, Boolean.toString(value));
            PreviewSessionFiles.replace(temporary, file);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    void render(UiRoot canvas) {
        boolean original = canvas.debugLines();
        canvas.debugLines(original || enabled.get());
        try {
            canvas.render();
        } finally {
            canvas.debugLines(original);
        }
    }

}
