package io.github.libfdx.graphics.shadergraph.ui;

/**
 * UI-thread notification for session, selection, or compilation changes.
 */
@FunctionalInterface
public interface ShaderGraphEditorSessionListener {
    void changed(ShaderGraphEditorSession session);
}
