package io.github.libfdx.graphics.shadergraph.ui;

/**
 * One deterministic, undoable editor-state transformation.
 *
 * <p>Commands execute on the UI thread. Compilation uses immutable
 * {@link ShaderGraphEditorCompileRequest} snapshots and may run on a worker.</p>
 */
public interface ShaderGraphEditorCommand {
    String name();

    ShaderGraphEditorState apply(ShaderGraphEditorState state);
}
