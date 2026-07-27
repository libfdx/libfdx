package io.github.libfdx.graphics.shadergraph.ir;

/**
 * Closed typed instruction vocabulary used between graph validation and WGSL
 * emission.
 */
public enum ShaderIrOpcode {
    CONSTANT,
    PARAMETER,
    RESOURCE,
    ADD,
    SUBTRACT,
    MULTIPLY,
    DIVIDE,
    MINIMUM,
    MAXIMUM,
    NEGATE,
    ABSOLUTE,
    NORMALIZE,
    DOT,
    CROSS,
    CLAMP,
    LERP,
    CONSTRUCT,
    CONVERT,
    MEMBER,
    BRANCH,
    SWITCH,
    LOOP,
    TEXTURE_SAMPLE,
    FUNCTION_CALL,
    DERIVATIVE_X,
    DERIVATIVE_Y,
    DISCARD,
    CUSTOM_FUNCTION,
    ATOMIC_ADD,
    STORAGE_LOAD,
    STORAGE_STORE,
    BARRIER
}
