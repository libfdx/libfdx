package io.github.libfdx.tests.utils.uikittest;

import io.github.libfdx.tests.utils.preview.DesktopPreviewEntry;
import io.github.libfdx.tests.utils.uikittest.showcase.UiKitShowcase;
import io.github.libfdx.tools.uipreview.UiPreviewContext;
import io.github.libfdx.ui.UiContent;

/** Preview fixture for the real UI Kit showcase composition. */
public final class UiKitTestPreview extends DesktopPreviewEntry {
    @Override
    public UiContent create(UiPreviewContext context) {
        return context.own(UiKitShowcase.previewContent(context.root(), context.assets())).content();
    }

}
