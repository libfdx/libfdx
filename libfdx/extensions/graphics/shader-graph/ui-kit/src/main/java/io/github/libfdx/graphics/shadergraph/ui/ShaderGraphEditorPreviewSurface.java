package io.github.libfdx.graphics.shadergraph.ui;

import io.github.libfdx.ui.UiCustomContent;
import io.github.libfdx.ui.UiScope;

/**
 * Optional preview host that also exposes a UI Kit custom surface.
 */
public interface ShaderGraphEditorPreviewSurface
        extends ShaderGraphEditorPreviewHost, UiCustomContent {
    /** Adds host-specific controls below the preview while it is visible. */
    default void buildControls(UiScope ui) {
    }

}
