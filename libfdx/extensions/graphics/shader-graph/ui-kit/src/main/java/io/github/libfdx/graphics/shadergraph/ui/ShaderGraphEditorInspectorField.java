package io.github.libfdx.graphics.shadergraph.ui;

import io.github.libfdx.core.FdxException;

/**
 * One stable inspector field. Editing hosts dispatch field IDs through the
 * typed semantic/document edit APIs rather than reflecting over Java objects.
 */
public final class ShaderGraphEditorInspectorField {
    private final String id;
    private final String label;
    private final String value;
    private final ShaderGraphEditorInspectorKind kind;
    private final boolean editable;

    public ShaderGraphEditorInspectorField(String id, String label,
            String value, ShaderGraphEditorInspectorKind kind,
            boolean editable) {
        if (empty(id) || empty(label) || value == null || kind == null) {
            throw new FdxException(
                    "Shader graph editor inspector field is incomplete");
        }
        this.id = id.trim();
        this.label = label.trim();
        this.value = value;
        this.kind = kind;
        this.editable = editable;
    }

    public String id() {
        return id;
    }

    public String label() {
        return label;
    }

    public String value() {
        return value;
    }

    public ShaderGraphEditorInspectorKind kind() {
        return kind;
    }

    public boolean editable() {
        return editable;
    }

    private static boolean empty(String value) {
        return value == null || value.trim().isEmpty();
    }
}
