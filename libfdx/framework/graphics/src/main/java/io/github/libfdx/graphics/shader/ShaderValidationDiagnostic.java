package io.github.libfdx.graphics.shader;

import io.github.libfdx.core.FdxException;

/**
 * Represents a shader validation diagnostic.
 *
 * @author xpenatan
 */
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

    /**
     * Creates a shader validation diagnostic.
     *
     * @param code the code
     * @param message the message
     * @return a new shader validation diagnostic
     */
    public static ShaderValidationDiagnostic error(String code, String message) {
        return new ShaderValidationDiagnostic(ShaderValidationSeverity.ERROR, code, message);
    }

    /**
     * Creates a shader validation diagnostic.
     *
     * @param code the code
     * @param message the message
     * @return a new shader validation diagnostic
     */
    public static ShaderValidationDiagnostic warning(String code, String message) {
        return new ShaderValidationDiagnostic(ShaderValidationSeverity.WARNING, code, message);
    }

    /**
     * Returns the severity.
     *
     * @return the severity
     */
    public ShaderValidationSeverity severity() {
        return severity;
    }

    /**
     * Returns the code.
     *
     * @return the code
     */
    public String code() {
        return code;
    }

    /**
     * Returns the message.
     *
     * @return the message
     */
    public String message() {
        return message;
    }
}
