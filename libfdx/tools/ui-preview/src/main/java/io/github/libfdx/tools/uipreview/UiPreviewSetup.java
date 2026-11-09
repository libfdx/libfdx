package io.github.libfdx.tools.uipreview;

import io.github.libfdx.ui.UiContent;

/** Sets up a preview's state and owned assets on the application thread. */
public interface UiPreviewSetup {
    /** Returns non-null content; queued assets finish loading before the first composition. */
    UiContent create(UiPreviewContext context);

}
