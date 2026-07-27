package io.github.libfdx.graphics.shadergraph.ui;

/**
 * Worker-safe shader graph editor compiler contract.
 */
public interface ShaderGraphEditorCompiler {
    ShaderGraphEditorCompilation compile(ShaderGraphEditorCompileRequest request);
}
