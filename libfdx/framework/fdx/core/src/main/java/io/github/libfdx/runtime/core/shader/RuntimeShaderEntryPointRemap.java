package io.github.libfdx.runtime.core.shader;

/**
 * Native entry-point identity emitted by a runtime shader target writer.
 *
 * @author xpenatan
 */
public final class RuntimeShaderEntryPointRemap {
    private final RuntimeShaderCompileStage stage;
    private final String sourceName;
    private final String targetName;

    RuntimeShaderEntryPointRemap(RuntimeShaderCompileStage stage, String sourceName, String targetName) {
        this.stage = stage;
        this.sourceName = sourceName;
        this.targetName = targetName;
    }

    /**
     * Creates one translated entry-point identity.
     *
     * @param stage the programmable stage
     * @param sourceName the canonical name
     * @param targetName the translated name
     * @return the remap
     */
    public static RuntimeShaderEntryPointRemap of(RuntimeShaderCompileStage stage,
            String sourceName, String targetName) {
        if (stage == null || stage == RuntimeShaderCompileStage.MODULE
                || sourceName == null || sourceName.trim().length() == 0
                || targetName == null || targetName.trim().length() == 0) {
            throw new IllegalArgumentException("Runtime shader entry-point remap is invalid");
        }
        return new RuntimeShaderEntryPointRemap(stage, sourceName.trim(), targetName.trim());
    }

    public RuntimeShaderCompileStage stage() {
        return stage;
    }

    public String sourceName() {
        return sourceName;
    }

    public String targetName() {
        return targetName;
    }
}
