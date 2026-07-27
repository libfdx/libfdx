package io.github.libfdx.graphics.shadergraph.ui;

import io.github.libfdx.core.FdxException;
import io.github.libfdx.graphics.shader.target.ShaderArtifactEncoding;
import java.nio.charset.StandardCharsets;

/**
 * One inspectable translated stage/module artifact returned to the editor.
 */
public final class ShaderGraphEditorArtifact {
    private final String targetId;
    private final String formatId;
    private final String environmentId;
    private final String compilerId;
    private final String stage;
    private final String entryPoint;
    private final ShaderArtifactEncoding encoding;
    private final byte[] payload;
    private final boolean verified;

    public ShaderGraphEditorArtifact(String targetId, String formatId,
            String environmentId, String compilerId, String stage,
            String entryPoint, ShaderArtifactEncoding encoding, byte[] payload,
            boolean verified) {
        if (empty(targetId) || empty(formatId) || empty(environmentId)
                || empty(compilerId) || empty(stage) || encoding == null
                || payload == null) {
            throw new FdxException("Shader graph editor artifact is incomplete");
        }
        this.targetId = targetId;
        this.formatId = formatId;
        this.environmentId = environmentId;
        this.compilerId = compilerId;
        this.stage = stage;
        this.entryPoint = entryPoint != null ? entryPoint : "";
        this.encoding = encoding;
        this.payload = payload.clone();
        this.verified = verified;
    }

    public static ShaderGraphEditorArtifact text(String targetId, String formatId,
            String environmentId, String compilerId, String stage,
            String entryPoint, String text, boolean verified) {
        return new ShaderGraphEditorArtifact(targetId, formatId, environmentId,
                compilerId, stage, entryPoint, ShaderArtifactEncoding.TEXT,
                (text != null ? text : "").getBytes(StandardCharsets.UTF_8), verified);
    }

    public String targetId() {
        return targetId;
    }

    public String formatId() {
        return formatId;
    }

    public String environmentId() {
        return environmentId;
    }

    public String compilerId() {
        return compilerId;
    }

    public String stage() {
        return stage;
    }

    public String entryPoint() {
        return entryPoint;
    }

    public ShaderArtifactEncoding encoding() {
        return encoding;
    }

    public byte[] payload() {
        return payload.clone();
    }

    public String text() {
        if (encoding != ShaderArtifactEncoding.TEXT) {
            throw new FdxException("Shader graph editor artifact is binary");
        }
        return new String(payload, StandardCharsets.UTF_8);
    }

    public boolean verified() {
        return verified;
    }

    private static boolean empty(String value) {
        return value == null || value.trim().isEmpty();
    }
}
