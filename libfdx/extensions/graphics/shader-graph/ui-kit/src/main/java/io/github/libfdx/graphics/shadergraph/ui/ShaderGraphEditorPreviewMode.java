package io.github.libfdx.graphics.shadergraph.ui;

import io.github.libfdx.graphics.shadergraph.model.ShaderGraph;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphKind;

/**
 * Provider-neutral preview compositions requested by the editor.
 */
public enum ShaderGraphEditorPreviewMode {
    NONE,
    FUNCTION_VALUE,
    MATERIAL_BALL,
    FULLSCREEN,
    MESH,
    COMPUTE_DATA,
    TECHNIQUE;

    public static ShaderGraphEditorPreviewMode defaultFor(ShaderGraphEditorDocument document) {
        if (document == null) {
            return NONE;
        }
        return switch (document.type()) {
            case PROGRAM -> MESH;
            case COMPUTE_PROGRAM -> COMPUTE_DATA;
            case TECHNIQUE, COMPUTE_TECHNIQUE -> TECHNIQUE;
            case GRAPH -> graphDefault(document.graph());
        };
    }

    public boolean supports(ShaderGraphEditorDocument document) {
        if (this == NONE || document == null) {
            return this == NONE;
        }
        return switch (document.type()) {
            case PROGRAM -> this == MESH || this == FULLSCREEN;
            case COMPUTE_PROGRAM -> this == COMPUTE_DATA;
            case TECHNIQUE, COMPUTE_TECHNIQUE -> this == TECHNIQUE;
            case GRAPH -> graphSupports(document.graph());
        };
    }

    private static ShaderGraphEditorPreviewMode graphDefault(ShaderGraph graph) {
        if (graph == null) {
            return NONE;
        }
        return switch (graph.kind()) {
            case FUNCTION, SUBGRAPH -> FUNCTION_VALUE;
            case SURFACE -> MATERIAL_BALL;
            case VERTEX, FRAGMENT, PROGRAM -> MESH;
            case COMPUTE -> COMPUTE_DATA;
            case TECHNIQUE -> TECHNIQUE;
        };
    }

    private boolean graphSupports(ShaderGraph graph) {
        if (graph == null) {
            return false;
        }
        ShaderGraphKind kind = graph.kind();
        return switch (kind) {
            case FUNCTION, SUBGRAPH -> this == FUNCTION_VALUE;
            case SURFACE -> this == MATERIAL_BALL;
            case VERTEX, FRAGMENT, PROGRAM -> this == MESH || this == FULLSCREEN;
            case COMPUTE -> this == COMPUTE_DATA;
            case TECHNIQUE -> this == TECHNIQUE;
        };
    }
}
