package io.github.libfdx.tools.shadergraph;

import io.github.libfdx.files.FileSystem;
import io.github.libfdx.graphics.shadergraph.ui.ShaderGraphEditorView;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AccessDeniedException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** One document in the desktop editor, with its own save destination and retained view. */
final class EditorEffect {
    final ShaderGraphDesktopEditor.Effect preset;
    final Path path;
    ShaderGraphEditorView view;

    EditorEffect(ShaderGraphDesktopEditor.Effect preset) {
        this.preset = preset;
        path = Path.of("assets", preset.assetPath()).toAbsolutePath().normalize();
    }

    EditorEffect(Path pickedFile) {
        preset = null;
        path = pickedFile;
    }

    String title() {
        return preset == null ? path.getFileName().toString() : preset.title();
    }

    String description() {
        if (preset == null) return path.toString();
        return preset.builder() == null ? preset.assetPath() : "Java graph · Save to " + preset.assetPath();
    }

    String read(FileSystem files) throws IOException {
        if (preset != null && !Files.exists(path)) {
            return files.internal(preset.assetPath()).readString(StandardCharsets.UTF_8).get();
        }
        return Files.readString(path);
    }

    boolean matches(Path pickedFile) throws IOException {
        // A Java builder's export is a separate file source when explicitly opened in the picker.
        if (preset != null && preset.builder() != null) return false;
        return path.equals(pickedFile) || Files.exists(path) && path.toRealPath().equals(pickedFile);
    }

    void save(String source) throws IOException {
        // Resolve a linked file before replacing it so the link keeps pointing at the edited document.
        Path target = Files.exists(path) ? path.toRealPath() : path;
        if (Files.exists(target) && !Files.isWritable(target)) {
            throw new AccessDeniedException(target.toString());
        }
        Files.createDirectories(target.getParent());
        Path temp = Files.createTempFile(target.getParent(), ".fdxgraph-", ".tmp");
        try {
            if (Files.exists(target)) {
                Files.copy(target, temp, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
            }
            Files.writeString(temp, source + System.lineSeparator());
            try {
                Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temp);
        }
    }

}
