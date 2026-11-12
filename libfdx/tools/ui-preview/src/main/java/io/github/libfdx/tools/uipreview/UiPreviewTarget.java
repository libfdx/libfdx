package io.github.libfdx.tools.uipreview;

import java.util.List;

/** A named composition factory and its optional children. Keep IDs stable and unique within an entry. */
public record UiPreviewTarget(String id, String label, UiPreviewSetup setup, List<UiPreviewTarget> children) {
    public UiPreviewTarget(String id, String label, UiPreviewSetup setup) {
        this(id, label, setup, List.of());
    }

    public UiPreviewTarget {
        if (id == null || !id.matches("[a-zA-Z0-9][a-zA-Z0-9._-]*")) {
            throw new IllegalArgumentException("Preview target ID must contain only letters, digits, dots, underscores or hyphens");
        }
        if (label == null || label.isBlank() || setup == null) {
            throw new IllegalArgumentException("Preview target label and setup required");
        }
        children = List.copyOf(children);
    }

    /** Creates a target that only needs a content factory. */
    public static UiPreviewTarget of(String id, String label, UiPreviewFactory factory) {
        if (factory == null) throw new IllegalArgumentException("Preview factory required");
        return new UiPreviewTarget(id, label, context -> factory.create());
    }

}
