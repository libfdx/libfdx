package io.github.libfdx.graphics.shadergraph.ui;

/**
 * Optional target compiler adapter. Hosts can bridge the common target
 * compiler registry without adding provider dependencies to this module.
 */
public interface ShaderGraphEditorArtifactCompiler {
    ShaderGraphEditorArtifact[] compile(ShaderGraphEditorCompileRequest request,
            String canonicalWgsl);
}
