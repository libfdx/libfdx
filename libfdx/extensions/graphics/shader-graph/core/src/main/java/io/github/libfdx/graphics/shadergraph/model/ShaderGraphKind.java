package io.github.libfdx.graphics.shadergraph.model;

/**
 * Semantic shader-graph asset kinds. Later phases activate the stage, program,
 * technique, and compute containers without changing the file vocabulary.
 */
public enum ShaderGraphKind {
    FUNCTION,
    SUBGRAPH,
    SURFACE,
    VERTEX,
    FRAGMENT,
    PROGRAM,
    TECHNIQUE,
    COMPUTE
}
