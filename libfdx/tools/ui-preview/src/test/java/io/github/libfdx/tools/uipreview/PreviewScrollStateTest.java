package io.github.libfdx.tools.uipreview;

import static org.junit.jupiter.api.Assertions.*;

import io.github.libfdx.collections.Array;
import io.github.libfdx.ui.Ui;
import io.github.libfdx.ui.UiListState;
import io.github.libfdx.ui.UiNode;
import io.github.libfdx.ui.UiRect;
import io.github.libfdx.ui.UiRoot;
import io.github.libfdx.ui.UiScrollState;
import io.github.libfdx.ui.UiToolkit;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class PreviewScrollStateTest {
    @Test
    void restoresNestedHorizontalAndVerticalOffsetsIntoFreshApplicationState() {
        PreviewScrollState snapshot;
        try (Canvas original = new Canvas(1000, 1400)) {
            original.scroll(120, 600, 80, 180);
            snapshot = PreviewScrollState.capture(original.root);
        }
        try (Canvas replacement = new Canvas(1000, 1400)) {
            snapshot.restore(replacement.root);
            assertEquals(120, replacement.outer.x(), 0.001f);
            assertEquals(600, replacement.outer.y(), 0.001f);
            assertEquals(80, replacement.inner.x(), 0.001f);
            assertEquals(180, replacement.inner.y(), 0.001f);
            assertEquals(-120, replacement.nodes[0].children().get(0).bounds().x(), 0.001f);
            assertEquals(-600, replacement.nodes[0].children().get(0).bounds().y(), 0.001f);
        }
    }

    @Test
    void shortenedContentClampsOffsetsAndRemovedScrollNodesAreIgnored() {
        try (Canvas original = new Canvas(1000, 1400); Canvas shortened = new Canvas(400, 260)) {
            original.scroll(400, 900, 60, 100);
            PreviewScrollState snapshot = PreviewScrollState.capture(original.root);
            snapshot.restore(shortened.root);
            assertEquals(shortened.outer.maxX(), shortened.outer.x(), 0.001f);
            assertEquals(shortened.outer.maxY(), shortened.outer.y(), 0.001f);
            shortened.root.setContent(ui -> ui.text("Scroll removed"));
            shortened.root.update(0);
            assertDoesNotThrow(() -> snapshot.restore(shortened.root));
        }
    }

    @Test
    void virtualListRestoresTheVisibleItemAndOffsetBeforeRendering() {
        Array<String> items = new Array<>();
        for (int i = 0; i < 100; i++) items.add("row-" + i);
        UiRoot original = root();
        UiRoot replacement = root();
        UiListState before = new UiListState();
        UiListState after = new UiListState();
        UiNode[] list = new UiNode[1];
        try {
            original.setContent(ui -> ui.virtualList(items.view(), before, 4, item -> item,
                    (row, item) -> row.panel(Ui.modifier().height(30), null)));
            original.update(0);
            before.scrollToItem(20, 7);
            original.update(0);
            replacement.setContent(ui -> list[0] = ui.virtualList(items.view(), after, 4, item -> item,
                    (row, item) -> row.panel(Ui.modifier().height(30), null)));
            replacement.update(0);
            PreviewScrollState.capture(original).restore(replacement);
            assertEquals(20, after.firstVisibleIndex());
            assertEquals(7, after.firstVisibleOffset(), 0.001f);
            assertEquals("row-20", list[0].children().get(0).value());
            assertEquals(-7, list[0].children().get(0).bounds().y(), 0.001f);
        } finally {
            original.dispose();
            replacement.dispose();
        }
    }

    @Test
    void workerHandoffIsTargetScopedAndWritesOnlyWhenOffsetsChange(@TempDir Path directory) throws Exception {
        Path file = directory.resolve(PreviewScrollHandoff.FILE);
        try (Canvas original = new Canvas(1000, 1400); Canvas replacement = new Canvas(1000, 1400)) {
            original.scroll(110, 600, 80, 180);
            PreviewScrollHandoff publisher = new PreviewScrollHandoff(file, null);
            publisher.publish(original.root, "example.Preview", "buttons", 0);
            FileTime marker = FileTime.fromMillis(1000);
            Files.setLastModifiedTime(file, marker);
            publisher.publish(original.root, "example.Preview", "buttons", 1_000_000_000L);
            assertEquals(marker, Files.getLastModifiedTime(file), "Idle frames must not serialize or rewrite snapshots");
            new PreviewScrollHandoff(null, file).restore(replacement.root, "example.Preview", "sliders");
            assertEquals(0, replacement.outer.y());
            new PreviewScrollHandoff(null, file).restore(replacement.root, "other.Preview", "buttons");
            assertEquals(0, replacement.outer.y());
            new PreviewScrollHandoff(null, file).restore(replacement.root, "example.Preview", "buttons");
            assertEquals(600, replacement.outer.y());
            assertEquals(80, replacement.inner.x());
            original.outer.scrollTo(110, 700);
            publisher.publish(original.root, "example.Preview", "buttons", 2_000_000_000L);
            assertNotEquals(marker, Files.getLastModifiedTime(file));
            new PreviewScrollHandoff(null, file).restore(replacement.root, "example.Preview", "buttons");
            assertEquals(700, replacement.outer.y());
        }
    }

    @Test
    void resetDiscardsPendingRestoreAndMalformedHandoffDoesNotBreakTheCanvas(@TempDir Path directory) throws Exception {
        Path file = directory.resolve(PreviewScrollHandoff.FILE);
        try (Canvas original = new Canvas(1000, 1400); Canvas replacement = new Canvas(1000, 1400)) {
            original.scroll(0, 600, 0, 0);
            new PreviewScrollHandoff(file, null).publish(original.root, "entry", "target", 0);
            PreviewScrollHandoff reset = new PreviewScrollHandoff(null, file);
            reset.discardRestore();
            reset.restore(replacement.root, "entry", "target");
            assertEquals(0, replacement.outer.y());
            Files.writeString(file, "entry=entry\ntarget=target\ncount=1\nscroll.0.identity=root\n");
            assertDoesNotThrow(() -> new PreviewScrollHandoff(null, file).restore(replacement.root, "entry", "target"));
            assertEquals(0, replacement.outer.y());
        }
    }

    private static UiRoot root() {
        UiRoot root = new UiToolkit(null).root(null, null).allowFontFallback(true);
        root.viewport(new UiRect(0, 0, 320, 200));
        return root;
    }

    private static final class Canvas implements AutoCloseable {
        final UiRoot root = root();
        final UiScrollState outer = new UiScrollState();
        final UiScrollState inner = new UiScrollState();
        final UiNode[] nodes = new UiNode[2];

        Canvas(float width, float height) {
            root.setContent(ui -> nodes[0] = ui.scrollView(Ui.modifier().fill(), outer,
                    body -> body.column(Ui.modifier().width(width).height(height), column -> {
                        nodes[1] = column.scrollView(Ui.modifier().width(280).height(120), inner,
                                nested -> nested.panel(Ui.modifier().width(800).height(500), null));
                    })));
            root.update(0);
        }

        void scroll(float x, float y, float nestedX, float nestedY) {
            outer.scrollTo(x, y);
            inner.scrollTo(nestedX, nestedY);
            root.requestCompose();
            root.update(0);
        }

        @Override
        public void close() {
            root.dispose();
        }

    }

}
