package io.github.libfdx.graphics.shader.target;

import io.github.libfdx.graphics.shader.ShaderValidationSeverity;
import io.github.libfdx.core.FdxException;

/**
 * Structured target compiler or verifier diagnostic.
 *
 * @author xpenatan
 */
public final class ShaderTargetDiagnostic {
    private final ShaderValidationSeverity severity;
    private final String code;
    private final String message;
    private final ShaderArtifactStage stage;
    private final String entryPoint;
    private final int line;
    private final int column;

    private ShaderTargetDiagnostic(ShaderValidationSeverity severity, String code, String message,
            ShaderArtifactStage stage, String entryPoint, int line, int column) {
        if (code == null || code.trim().length() == 0 || message == null || message.trim().length() == 0) {
            throw new FdxException("Shader target diagnostic code and message cannot be empty");
        }
        this.severity = severity != null ? severity : ShaderValidationSeverity.ERROR;
        this.code = code.trim();
        this.message = message.trim();
        this.stage = stage;
        this.entryPoint = entryPoint != null ? entryPoint : "";
        this.line = line;
        this.column = column;
    }

    public static ShaderTargetDiagnostic error(String code, String message) {
        return new ShaderTargetDiagnostic(ShaderValidationSeverity.ERROR, code, message, null, "", -1, -1);
    }

    public static ShaderTargetDiagnostic warning(String code, String message) {
        return new ShaderTargetDiagnostic(ShaderValidationSeverity.WARNING, code, message, null, "", -1, -1);
    }

    public static ShaderTargetDiagnostic at(ShaderValidationSeverity severity, String code, String message,
            ShaderArtifactStage stage, String entryPoint, int line, int column) {
        return new ShaderTargetDiagnostic(severity, code, message, stage, entryPoint, line, column);
    }

    public ShaderValidationSeverity severity() {
        return severity;
    }

    public String code() {
        return code;
    }

    public String message() {
        return message;
    }

    public ShaderArtifactStage stage() {
        return stage;
    }

    public String entryPoint() {
        return entryPoint;
    }

    public int line() {
        return line;
    }

    public int column() {
        return column;
    }
}
