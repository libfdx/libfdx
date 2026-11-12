package io.github.libfdx.tests.utils.uikittest;

import io.github.libfdx.tests.utils.preview.DesktopPreviewEntry;
import io.github.libfdx.tests.utils.uikittest.showcase.UiKitShowcase;
import io.github.libfdx.tools.uipreview.UiPreviewConfig;
import io.github.libfdx.tools.uipreview.UiPreviewContext;
import io.github.libfdx.tools.uipreview.UiPreviewTarget;
import io.github.libfdx.ui.UiContent;
import java.util.ArrayList;
import java.util.List;

/** Registers the full showcase and its isolated sections in the preview tool. */
public final class UiKitTestPreview extends DesktopPreviewEntry {
    @Override
    public String previewLabel() {
        return "UI Kit Test";
    }

    @Override
    public UiPreviewConfig previewConfig() {
        return super.previewConfig().interactive(true);
    }

    @Override
    public String initialPreviewTarget() {
        int section = UiKitShowcase.sectionIndex(System.getProperty("libfdx.uiPreview.section", ""));
        return section < 0 ? "" : UiKitShowcase.sectionId(section);
    }

    @Override
    public List<UiPreviewTarget> previewChildren() {
        List<UiPreviewTarget> sections = new ArrayList<>();
        for (int i = 0; i < UiKitShowcase.sectionCount(); i++) {
            int section = i;
            sections.add(new UiPreviewTarget(UiKitShowcase.sectionId(section), UiKitShowcase.sectionLabel(section),
                    context -> section(context, section)));
        }
        return sections;
    }

    @Override
    public UiContent create(UiPreviewContext context) {
        return context.own(UiKitShowcase.previewContent(context.root(), context.assets())).content();
    }

    private UiContent section(UiPreviewContext context, int section) {
        return context.own(UiKitShowcase.previewSection(context.root(), context.assets(), section)).content();
    }

}
