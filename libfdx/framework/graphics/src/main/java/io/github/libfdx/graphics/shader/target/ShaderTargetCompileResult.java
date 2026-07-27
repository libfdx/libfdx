package io.github.libfdx.graphics.shader.target;

import io.github.libfdx.graphics.shader.ShaderValidationSeverity;
import io.github.libfdx.core.FdxException;

/**
 * Result returned by a target compiler or compiler registry.
 *
 * @author xpenatan
 */
public final class ShaderTargetCompileResult {
    private final ShaderTargetArtifact artifact;
    private final ShaderTargetDiagnostic[] diagnostics;

    private ShaderTargetCompileResult(ShaderTargetArtifact artifact, ShaderTargetDiagnostic[] diagnostics) {
        this.artifact = artifact;
        this.diagnostics = diagnostics != null ? diagnostics.clone() : new ShaderTargetDiagnostic[0];
    }

    public static ShaderTargetCompileResult success(ShaderTargetArtifact artifact) {
        if (artifact == null) {
            throw new FdxException("Successful shader target compilation requires an artifact");
        }
        return new ShaderTargetCompileResult(artifact, null);
    }

    public static ShaderTargetCompileResult success(ShaderTargetArtifact artifact,
            ShaderTargetDiagnostic[] diagnostics) {
        if (artifact == null) {
            throw new FdxException("Successful shader target compilation requires an artifact");
        }
        return new ShaderTargetCompileResult(artifact, diagnostics);
    }

    public static ShaderTargetCompileResult failure(ShaderTargetDiagnostic... diagnostics) {
        if (diagnostics == null || diagnostics.length == 0) {
            throw new FdxException("Failed shader target compilation requires a diagnostic");
        }
        return new ShaderTargetCompileResult(null, diagnostics);
    }

    public boolean success() {
        if (artifact == null) {
            return false;
        }
        for (ShaderTargetDiagnostic diagnostic : diagnostics) {
            if (diagnostic != null && diagnostic.severity() == ShaderValidationSeverity.ERROR) {
                return false;
            }
        }
        return true;
    }

    public ShaderTargetArtifact artifact() {
        return artifact;
    }

    public ShaderTargetDiagnostic[] diagnostics() {
        return diagnostics.clone();
    }

    public void throwIfFailed(String label) {
        if (success()) {
            return;
        }
        StringBuilder message = new StringBuilder("Shader target compilation failed");
        if (label != null && label.length() > 0) {
            message.append(" for ").append(label);
        }
        for (ShaderTargetDiagnostic diagnostic : diagnostics) {
            if (diagnostic != null) {
                message.append('\n').append(diagnostic.code()).append(": ").append(diagnostic.message());
            }
        }
        throw new FdxException(message.toString());
    }
}
