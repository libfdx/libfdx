package io.github.libfdx.runtime.core.shader;

/**
 * Represents a shader compiler diagnostic.
 *
 * @author xpenatan
 */
public final class RuntimeShaderCompileDiagnostic {
    private final String message;

    private RuntimeShaderCompileDiagnostic(String message) {
        this.message = message != null ? message : "";
    }

    /**
     * Creates a diagnostic.
     *
     * @param message the message
     * @return a new diagnostic
     */
    public static RuntimeShaderCompileDiagnostic of(String message) {
        return new RuntimeShaderCompileDiagnostic(message);
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
