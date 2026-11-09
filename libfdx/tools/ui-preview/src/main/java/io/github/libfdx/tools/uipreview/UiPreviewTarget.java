package io.github.libfdx.tools.uipreview;

/** A named composition factory. Keep the ID stable across edits and use a label for the picker and window title. */
public record UiPreviewTarget(String id, String label, UiPreviewSetup setup) {
    public UiPreviewTarget {
        if (id == null || !id.matches("[a-zA-Z0-9][a-zA-Z0-9._-]*")) {
            throw new IllegalArgumentException("Preview target ID must contain only letters, digits, dots, underscores or hyphens");
        }
        if (label == null || label.isBlank() || setup == null) {
            throw new IllegalArgumentException("Preview target label and setup required");
        }
    }

    /** Creates a target that only needs a content factory. */
    public static UiPreviewTarget of(String id, String label, UiPreviewFactory factory) {
        if (factory == null) throw new IllegalArgumentException("Preview factory required");
        return new UiPreviewTarget(id, label, context -> factory.create());
    }

}
