package io.github.libfdx.graphics.shadergraph.ui;

import io.github.libfdx.core.FdxException;
import java.util.Objects;

/**
 * Immutable semantic document and editor-only layout snapshot.
 */
public final class ShaderGraphEditorState {
    private final ShaderGraphEditorDocument document;
    private final ShaderGraphEditorLayout layout;

    public ShaderGraphEditorState(ShaderGraphEditorDocument document,
            ShaderGraphEditorLayout layout) {
        if (document == null || layout == null) {
            throw new FdxException("Shader graph editor state is incomplete");
        }
        this.document = document;
        this.layout = layout.reconcile(document);
    }

    public ShaderGraphEditorDocument document() {
        return document;
    }

    public ShaderGraphEditorLayout layout() {
        return layout;
    }

    public ShaderGraphEditorState document(ShaderGraphEditorDocument value) {
        return new ShaderGraphEditorState(value, layout.reconcile(value));
    }

    public ShaderGraphEditorState layout(ShaderGraphEditorLayout value) {
        return new ShaderGraphEditorState(document, value);
    }

    @Override
    public boolean equals(Object object) {
        return object instanceof ShaderGraphEditorState other
                && document.equals(other.document) && layout.equals(other.layout);
    }

    @Override
    public int hashCode() {
        return Objects.hash(document, layout);
    }
}
