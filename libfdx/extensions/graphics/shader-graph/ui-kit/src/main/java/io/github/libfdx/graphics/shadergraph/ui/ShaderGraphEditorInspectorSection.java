package io.github.libfdx.graphics.shadergraph.ui;

import io.github.libfdx.core.FdxException;

/**
 * Stable inspector section used by UI Kit or a custom editor frontend.
 */
public final class ShaderGraphEditorInspectorSection {
    private final String id;
    private final String title;
    private final ShaderGraphEditorInspectorField[] fields;

    public ShaderGraphEditorInspectorSection(String id, String title,
            ShaderGraphEditorInspectorField... fields) {
        if (empty(id) || empty(title) || fields == null) {
            throw new FdxException(
                    "Shader graph editor inspector section is incomplete");
        }
        this.id = id.trim();
        this.title = title.trim();
        this.fields = fields.clone();
        for (ShaderGraphEditorInspectorField field : this.fields) {
            if (field == null) {
                throw new FdxException(
                        "Shader graph editor inspector field cannot be null");
            }
        }
    }

    public String id() {
        return id;
    }

    public String title() {
        return title;
    }

    public ShaderGraphEditorInspectorField[] fields() {
        return fields.clone();
    }

    private static boolean empty(String value) {
        return value == null || value.trim().isEmpty();
    }
}
