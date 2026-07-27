package io.github.libfdx.graphics.shader.target;

/**
 * Built-in shader artifact formats.
 *
 * @author xpenatan
 */
public final class ShaderArtifactFormats {
    public static final ShaderArtifactFormat WGSL_TEXT = ShaderArtifactFormat.of(
            "wgsl-text", ShaderArtifactEncoding.TEXT, "text/wgsl");
    public static final ShaderArtifactFormat GLSL_TEXT = ShaderArtifactFormat.of(
            "glsl-text", ShaderArtifactEncoding.TEXT, "text/glsl");
    public static final ShaderArtifactFormat GLSL_ES_TEXT = ShaderArtifactFormat.of(
            "glsl-es-text", ShaderArtifactEncoding.TEXT, "text/glsl-es");
    public static final ShaderArtifactFormat SPIRV_BINARY = ShaderArtifactFormat.of(
            "spirv-binary", ShaderArtifactEncoding.BINARY, "application/spirv");
    public static final ShaderArtifactFormat MSL_TEXT = ShaderArtifactFormat.of(
            "msl-text", ShaderArtifactEncoding.TEXT, "text/metal");
    public static final ShaderArtifactFormat HLSL_TEXT = ShaderArtifactFormat.of(
            "hlsl-text", ShaderArtifactEncoding.TEXT, "text/hlsl");

    private ShaderArtifactFormats() {
    }
}
