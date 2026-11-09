package io.github.libfdx.tools.uipreview;

import io.github.libfdx.ui.UiContent;

/** Creates fresh preview state/content on the application thread at startup and reset. */
public interface UiPreviewFactory {
    /** Returns non-null content. Create retained state here, outside the returned content builder. */
    UiContent create();

}
