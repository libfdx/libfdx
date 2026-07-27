package io.github.libfdx.graphics.shadergraph.ui;

/**
 * Provider-neutral boundary for a rendered editor preview.
 *
 * <p>The editor calls this only for an accepted successful compilation.
 * Failed or stale results leave the host's last-good preview untouched.</p>
 */
public interface ShaderGraphEditorPreviewHost {
    void present(ShaderGraphEditorCompilation compilation,
            ShaderGraphEditorPreviewMode mode);
}
