package io.github.libfdx.graphics.shader.target;

import io.github.libfdx.graphics.shader.ShaderStage;
import io.github.libfdx.core.FdxException;

/**
 * Selects one canonical shader entry point for target compilation.
 *
 * @author xpenatan
 */
public final class ShaderEntryPointSelection implements Comparable<ShaderEntryPointSelection> {
    private final ShaderArtifactStage stage;
    private final String entryPoint;

    private ShaderEntryPointSelection(ShaderArtifactStage stage, String entryPoint) {
        if (stage == null || stage == ShaderArtifactStage.MODULE) {
            throw new FdxException("Shader entry-point selection requires a programmable stage");
        }
        if (entryPoint == null || entryPoint.trim().length() == 0) {
            throw new FdxException("Shader entry-point selection name cannot be empty");
        }
        this.stage = stage;
        this.entryPoint = entryPoint.trim();
    }

    /**
     * Creates a selection.
     *
     * @param stage the stage
     * @param entryPoint the canonical entry-point name
     * @return the selection
     */
    public static ShaderEntryPointSelection of(ShaderArtifactStage stage, String entryPoint) {
        return new ShaderEntryPointSelection(stage, entryPoint);
    }

    /**
     * Creates a selection.
     *
     * @param stage the stage
     * @param entryPoint the canonical entry-point name
     * @return the selection
     */
    public static ShaderEntryPointSelection of(ShaderStage stage, String entryPoint) {
        return new ShaderEntryPointSelection(ShaderArtifactStage.of(stage), entryPoint);
    }

    /**
     * Returns the stage.
     *
     * @return the stage
     */
    public ShaderArtifactStage stage() {
        return stage;
    }

    /**
     * Returns the canonical entry-point name.
     *
     * @return the name
     */
    public String entryPoint() {
        return entryPoint;
    }

    @Override
    public int compareTo(ShaderEntryPointSelection other) {
        if (other == null) {
            return 1;
        }
        int stageOrder = stage.compareTo(other.stage);
        return stageOrder != 0 ? stageOrder : entryPoint.compareTo(other.entryPoint);
    }
}
