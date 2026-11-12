package io.github.libfdx.tools.uipreview;

import java.util.List;

/**
 * Opts a concrete class into automatic preview discovery. Provide a non-private
 * zero-argument constructor and keep it free of game startup logic. Create content
 * and its resources in {@link #create(UiPreviewContext)} on the application thread.
 */
public interface UiPreviewEntry extends UiPreviewSetup {
    /** Label for the entry root in its preview hierarchy. */
    default String previewLabel() {
        return getClass().getSimpleName();
    }

    /** Declares children without creating their UI, state, or assets. */
    default List<UiPreviewTarget> previewChildren() {
        return List.of();
    }

    /** Initial child ID for this entry, or an empty string to open the entry root. */
    default String initialPreviewTarget() {
        return "";
    }

    /** Configures the standalone window for this entry. */
    default UiPreviewConfig previewConfig() {
        return new UiPreviewConfig(960, 640);
    }

    /** Launches the entry using the Java 25 program launcher. Leave this method inherited. */
    default void main(String[] args) {
        UiPreview.openEntry(previewConfig(), this);
    }

}
