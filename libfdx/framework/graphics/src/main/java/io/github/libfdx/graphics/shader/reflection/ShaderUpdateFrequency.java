package io.github.libfdx.graphics.shader.reflection;

/**
 * Describes how frequently a shader parameter or resource normally changes.
 */
public enum ShaderUpdateFrequency {
    UNSPECIFIED,
    FRAME,
    PASS,
    DRAW,
    ON_CHANGE,
    MIXED
}
