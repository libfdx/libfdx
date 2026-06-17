package io.github.libfdx.tools.shader;

import io.github.libfdx.core.FdxException;

/**
 * Represents a shader compiler diagnostic.
 *
 * @author xpenatan
 */
public final class FdxShaderCompilerDiagnostic {
    private final String message;

    private FdxShaderCompilerDiagnostic(String message) {
        if (message == null || message.trim().isEmpty()) {
            throw new FdxException("Shader compiler diagnostic message cannot be empty");
        }
        this.message = message;
    }

    /**
     * Creates a diagnostic.
     *
     * @param message the message
     * @return a new diagnostic
     */
    public static FdxShaderCompilerDiagnostic of(String message) {
        return new FdxShaderCompilerDiagnostic(message);
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
