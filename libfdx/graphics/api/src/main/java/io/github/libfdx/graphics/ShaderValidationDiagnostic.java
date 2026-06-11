package io.github.libfdx.graphics;

import io.github.libfdx.core.FdxException;

public final class ShaderValidationDiagnostic {
    private final ShaderValidationSeverity severity;
    private final String code;
    private final String message;

    private ShaderValidationDiagnostic(ShaderValidationSeverity severity, String code, String message) {
        if (code == null || code.trim().isEmpty()) {
            throw new FdxException("Shader validation diagnostic code cannot be empty");
        }
        if (message == null || message.trim().isEmpty()) {
            throw new FdxException("Shader validation diagnostic message cannot be empty");
        }
        this.severity = severity != null ? severity : ShaderValidationSeverity.ERROR;
        this.code = code;
        this.message = message;
    }

    public static ShaderValidationDiagnostic error(String code, String message) {
        return new ShaderValidationDiagnostic(ShaderValidationSeverity.ERROR, code, message);
    }

    public static ShaderValidationDiagnostic warning(String code, String message) {
        return new ShaderValidationDiagnostic(ShaderValidationSeverity.WARNING, code, message);
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
}
