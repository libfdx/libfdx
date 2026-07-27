package io.github.libfdx.graphics.shader.target;

import io.github.libfdx.graphics.shader.ShaderStage;

/**
 * Identifies the module or entry-point stage represented by one target artifact.
 *
 * @author xpenatan
 */
public enum ShaderArtifactStage {
    MODULE,
    VERTEX,
    FRAGMENT,
    COMPUTE;

    /**
     * Converts a programmable shader stage.
     *
     * @param stage the shader stage
     * @return the artifact stage
     */
    public static ShaderArtifactStage of(ShaderStage stage) {
        if (stage == ShaderStage.VERTEX) {
            return VERTEX;
        }
        if (stage == ShaderStage.FRAGMENT) {
            return FRAGMENT;
        }
        return COMPUTE;
    }
}
