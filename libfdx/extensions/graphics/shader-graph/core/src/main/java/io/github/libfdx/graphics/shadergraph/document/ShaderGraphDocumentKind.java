package io.github.libfdx.graphics.shadergraph.document;

import io.github.libfdx.core.FdxException;

/**
 * Identifies the semantic asset stored by a shader-graph document.
 */
public enum ShaderGraphDocumentKind {
    GRAPH("graph"),
    PROGRAM("program"),
    COMPUTE_PROGRAM("compute-program"),
    TECHNIQUE("technique"),
    COMPUTE_TECHNIQUE("compute-technique");

    private final String id;

    ShaderGraphDocumentKind(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public static ShaderGraphDocumentKind fromId(String id) {
        if (id != null) {
            String normalized = id.trim();
            for (ShaderGraphDocumentKind kind : values()) {
                if (kind.id.equals(normalized)) {
                    return kind;
                }
            }
        }
        throw new FdxException("Unknown shader graph document kind: " + id);
    }
}
