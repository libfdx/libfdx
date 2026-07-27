package io.github.libfdx.graphics.shadergraph.model;

/**
 * Declares who supplies a graph parameter and whether it is dynamic.
 */
public enum ShaderGraphParameterKind {
    FUNCTION_INPUT,
    MATERIAL,
    STATIC_SWITCH,
    STAGE_INPUT
}
