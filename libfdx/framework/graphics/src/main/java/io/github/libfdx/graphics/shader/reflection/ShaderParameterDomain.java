package io.github.libfdx.graphics.shader.reflection;

/**
 * Identifies the framework owner of a shader parameter or resource.
 */
public enum ShaderParameterDomain {
    UNSPECIFIED,
    FRAME_VIEW,
    ENVIRONMENT_PASS,
    OBJECT_DRAW,
    MATERIAL,
    MIXED
}
