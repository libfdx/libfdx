package io.github.libfdx.tools.shadergraph;

import io.github.libfdx.files.DefaultFileSystem;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class EditorEffectTest {
    @TempDir
    Path directory;

    @Test
    void pickedFilesWithTheSameNameKeepSeparateDestinations() throws IOException {
        Path first = Files.writeString(directory.resolve("surface.fdxgraph"), "first").toRealPath();
        Path second = Files.createDirectories(directory.resolve("other")).resolve("surface.fdxgraph");
        Files.writeString(second, "second");
        var effect = new EditorEffect(first);
        var files = new DefaultFileSystem();

        assertTrue(effect.matches(directory.resolve("other/../surface.fdxgraph").toRealPath()));
        assertFalse(effect.matches(second.toRealPath()));
        assertEquals("first", effect.read(files));
        effect.save("edited");
        assertEquals("edited" + System.lineSeparator(), Files.readString(first));
        assertEquals("second", Files.readString(second));
    }

    @Test
    void pickingAJavaExportUsesTheFileAsItsOwnSource() throws IOException {
        var preset = new ShaderGraphDesktopEditor.Effect("Effect", "effect-from-code.fdxgraph", () -> null, null);
        var javaEffect = new EditorEffect(preset);
        assertFalse(javaEffect.matches(javaEffect.path));
    }

}
