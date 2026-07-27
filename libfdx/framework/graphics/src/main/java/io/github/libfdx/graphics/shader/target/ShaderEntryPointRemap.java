package io.github.libfdx.graphics.shader.target;

import io.github.libfdx.core.FdxException;

/**
 * Maps one canonical entry point to its translated target identity.
 *
 * @author xpenatan
 */
public final class ShaderEntryPointRemap implements Comparable<ShaderEntryPointRemap> {
    private final ShaderArtifactStage stage;
    private final String sourceName;
    private final String targetName;

    private ShaderEntryPointRemap(ShaderArtifactStage stage, String sourceName, String targetName) {
        if (stage == null || stage == ShaderArtifactStage.MODULE) {
            throw new FdxException("Shader entry-point remap requires a programmable stage");
        }
        if (sourceName == null || sourceName.trim().length() == 0
                || targetName == null || targetName.trim().length() == 0) {
            throw new FdxException("Shader entry-point remap names cannot be empty");
        }
        this.stage = stage;
        this.sourceName = sourceName.trim();
        this.targetName = targetName.trim();
    }

    /**
     * Creates a remap.
     *
     * @param stage the stage
     * @param sourceName the canonical name
     * @param targetName the translated name
     * @return the remap
     */
    public static ShaderEntryPointRemap of(ShaderArtifactStage stage, String sourceName, String targetName) {
        return new ShaderEntryPointRemap(stage, sourceName, targetName);
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
     * Returns the canonical name.
     *
     * @return the canonical name
     */
    public String sourceName() {
        return sourceName;
    }

    /**
     * Returns the translated name.
     *
     * @return the translated name
     */
    public String targetName() {
        return targetName;
    }

    @Override
    public int compareTo(ShaderEntryPointRemap other) {
        if (other == null) {
            return 1;
        }
        int comparison = stage.compareTo(other.stage);
        return comparison != 0 ? comparison : sourceName.compareTo(other.sourceName);
    }
}
