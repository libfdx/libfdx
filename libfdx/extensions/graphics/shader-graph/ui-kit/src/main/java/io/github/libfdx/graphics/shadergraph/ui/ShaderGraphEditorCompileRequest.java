package io.github.libfdx.graphics.shadergraph.ui;

/**
 * Immutable worker-safe snapshot for one editor compilation generation.
 */
public final class ShaderGraphEditorCompileRequest {
    private final long generation;
    private final long semanticRevision;
    private final ShaderGraphEditorDocument document;
    private final ShaderGraphEditorCompileSettings settings;

    ShaderGraphEditorCompileRequest(long generation, long semanticRevision,
            ShaderGraphEditorDocument document, ShaderGraphEditorCompileSettings settings) {
        this.generation = generation;
        this.semanticRevision = semanticRevision;
        this.document = document;
        this.settings = settings;
    }

    public long generation() {
        return generation;
    }

    public long semanticRevision() {
        return semanticRevision;
    }

    public ShaderGraphEditorDocument document() {
        return document;
    }

    public ShaderGraphEditorCompileSettings settings() {
        return settings;
    }
}
