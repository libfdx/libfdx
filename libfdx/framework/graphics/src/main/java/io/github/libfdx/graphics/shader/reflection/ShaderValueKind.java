package io.github.libfdx.graphics.shader.reflection;

/**
 * Lists the structural kinds of reflected shader values.
 */
public enum ShaderValueKind {
    SCALAR,
    VECTOR,
    MATRIX,
    ARRAY,
    STRUCT,
    ATOMIC,
    BUFFER,
    UNKNOWN
}
