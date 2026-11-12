package io.github.libfdx.tools.uipreview;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.libfdx.ui.UiNode;
import io.github.libfdx.ui.UiRenderer;
import io.github.libfdx.ui.UiRoot;
import io.github.libfdx.ui.UiToolkit;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class PreviewDebugLinesTest {
    @Test
    void savesTheToggleForReloadsAndRestoresTheCanvasDebugState(@TempDir Path directory) {
        Path file = directory.resolve("debug-lines");
        PreviewDebugLines debug = new PreviewDebugLines(file);
        RecordingRenderer renderer = new RecordingRenderer();
        UiRoot canvas = new UiToolkit(null).root(null, null).allowFontFallback(true);
        canvas.renderer(renderer);
        canvas.resize(200, 120);
        canvas.setContent(ui -> ui.text("Previewed UI"));
        canvas.update(0);
        try {
            assertFalse(debug.state().get());
            debug.state().set(true);
            debug.save();
            assertTrue(new PreviewDebugLines(file).state().get());
            debug.render(canvas);
            assertTrue(renderer.lines);
            assertFalse(canvas.debugLines());

            debug.state().set(false);
            debug.save();
            assertFalse(new PreviewDebugLines(file).state().get());
            debug.render(canvas);
            assertFalse(renderer.lines);
            canvas.debugLines(true);
            debug.render(canvas);
            assertTrue(renderer.lines);
            assertTrue(canvas.debugLines());
        } finally {
            canvas.dispose();
        }
    }

    private static final class RecordingRenderer implements UiRenderer {
        private boolean lines;

        @Override
        public void render(UiRoot root, UiNode node) {
            lines = root.debugLines();
        }

        @Override
        public void dispose() {
        }

        @Override
        public boolean isDisposed() {
            return false;
        }

    }

}
