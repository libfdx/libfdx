package io.github.libfdx.tools.uipreview;

import io.github.libfdx.ui.UiRoot;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Target-scoped viewport handoff between automatic worker generations. */
final class PreviewScrollHandoff {
    static final String FILE = "scroll-state.properties";
    static final String RESTORE_PROPERTY = "libfdx.uiPreview.scrollRestore";

    private final Path output;
    private Path restore;
    private long nextPublish;
    private long published;
    private boolean hasPublished;
    private String publishedEntry;
    private String publishedTarget;

    PreviewScrollHandoff(Path output, Path restore) {
        this.output = output;
        this.restore = restore;
    }

    void discardRestore() {
        restore = null;
    }

    void restore(UiRoot root, String entry, String target) {
        Path source = restore;
        restore = null;
        if (source == null || !Files.isRegularFile(source)) return;
        try {
            PreviewScrollState.read(PreviewReloadRequest.readValues(source), entry, target).restore(root);
        } catch (IOException | IllegalArgumentException failure) {
            System.err.println("[ui-preview] Cannot restore scroll position: " + failure);
        }
    }

    void publish(UiRoot root, String entry, String target, long now) {
        if (output == null || now < nextPublish) return;
        nextPublish = now + 150_000_000L;
        long fingerprint = PreviewScrollState.fingerprint(root.rootNode(), 1);
        if (hasPublished && published == fingerprint && entry.equals(publishedEntry) && target.equals(publishedTarget)) return;
        try {
            PreviewReloadRequest.write(output, PreviewScrollState.capture(root).values(entry, target));
            published = fingerprint;
            publishedEntry = entry;
            publishedTarget = target;
            hasPublished = true;
        } catch (IOException failure) {
            System.err.println("[ui-preview] Cannot save scroll position: " + failure);
        }
    }

}
