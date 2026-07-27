package io.github.libfdx.graphics.shader.target;

import io.github.libfdx.core.FdxException;

import java.nio.charset.StandardCharsets;

/**
 * Immutable translated module or entry-point payload.
 *
 * @author xpenatan
 */
public final class ShaderStageArtifact implements Comparable<ShaderStageArtifact> {
    private final ShaderArtifactStage stage;
    private final String entryPoint;
    private final ShaderArtifactFormat format;
    private final byte[] payload;

    private ShaderStageArtifact(ShaderArtifactStage stage, String entryPoint,
            ShaderArtifactFormat format, byte[] payload) {
        if (stage == null || format == null) {
            throw new FdxException("Shader stage artifact stage and format cannot be null");
        }
        String name = entryPoint != null ? entryPoint.trim() : "";
        if (stage == ShaderArtifactStage.MODULE && name.length() != 0) {
            throw new FdxException("Module shader artifact cannot declare an entry-point name");
        }
        if (stage != ShaderArtifactStage.MODULE && name.length() == 0) {
            throw new FdxException("Stage shader artifact entry point cannot be empty");
        }
        if (payload == null || payload.length == 0) {
            throw new FdxException("Shader stage artifact payload cannot be empty");
        }
        this.stage = stage;
        this.entryPoint = name;
        this.format = format;
        this.payload = payload.clone();
    }

    /**
     * Creates a text artifact.
     *
     * @param stage the artifact stage
     * @param entryPoint the translated entry point, empty for a module
     * @param format the text format
     * @param source the source
     * @return the artifact
     */
    public static ShaderStageArtifact text(ShaderArtifactStage stage, String entryPoint,
            ShaderArtifactFormat format, String source) {
        if (format == null || format.encoding() != ShaderArtifactEncoding.TEXT) {
            throw new FdxException("Text shader artifact requires a text format");
        }
        if (source == null || source.length() == 0) {
            throw new FdxException("Shader stage artifact source cannot be empty");
        }
        return new ShaderStageArtifact(stage, entryPoint, format, source.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Creates a binary artifact.
     *
     * @param stage the artifact stage
     * @param entryPoint the translated entry point
     * @param format the binary format
     * @param bytes the bytes
     * @return the artifact
     */
    public static ShaderStageArtifact binary(ShaderArtifactStage stage, String entryPoint,
            ShaderArtifactFormat format, byte[] bytes) {
        if (format == null || format.encoding() != ShaderArtifactEncoding.BINARY) {
            throw new FdxException("Binary shader artifact requires a binary format");
        }
        return new ShaderStageArtifact(stage, entryPoint, format, bytes);
    }

    public ShaderArtifactStage stage() {
        return stage;
    }

    public String entryPoint() {
        return entryPoint;
    }

    public ShaderArtifactFormat format() {
        return format;
    }

    public byte[] payload() {
        return payload.clone();
    }

    /**
     * Returns the UTF-8 source for a text artifact.
     *
     * @return the source
     */
    public String text() {
        if (format.encoding() != ShaderArtifactEncoding.TEXT) {
            throw new FdxException("Shader artifact " + format + " is not text");
        }
        return new String(payload, StandardCharsets.UTF_8);
    }

    @Override
    public int compareTo(ShaderStageArtifact other) {
        if (other == null) {
            return 1;
        }
        int comparison = stage.compareTo(other.stage);
        return comparison != 0 ? comparison : entryPoint.compareTo(other.entryPoint);
    }
}
