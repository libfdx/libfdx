package io.github.libfdx.graphics;

import io.github.libfdx.core.FdxException;

/**
 * Represents the result of a shader validation operation.
 *
 * @author xpenatan
 */
public final class ShaderValidationResult {
    private static final ShaderValidationDiagnostic[] EMPTY_DIAGNOSTICS = new ShaderValidationDiagnostic[0];
    private static final ShaderValidationResult SUCCESS = new ShaderValidationResult(EMPTY_DIAGNOSTICS);

    private final ShaderValidationDiagnostic[] diagnostics;

    private ShaderValidationResult(ShaderValidationDiagnostic[] diagnostics) {
        this.diagnostics = diagnostics != null ? diagnostics.clone() : EMPTY_DIAGNOSTICS;
    }

    /**
     * Creates a shader validation result.
     *
     * @return a new shader validation result
     */
    public static ShaderValidationResult success() {
        return SUCCESS;
    }

    /**
     * Creates a shader validation result from the supplied values.
     *
     * @param diagnostics the diagnostics
     * @return a new shader validation result
     */
    public static ShaderValidationResult of(ShaderValidationDiagnostic[] diagnostics) {
        if (diagnostics == null || diagnostics.length == 0) {
            return SUCCESS;
        }
        return new ShaderValidationResult(diagnostics);
    }

    /**
     * Returns the success status.
     *
     * @return true if success status succeeds or is active; false otherwise
     */
    public boolean successStatus() {
        return errorCount() == 0;
    }

    /**
     * Returns whether success is enabled or true.
     *
     * @return true if success is enabled or true; false otherwise
     */
    public boolean isSuccess() {
        return successStatus();
    }

    /**
     * Returns the error count.
     *
     * @return the error count
     */
    public int errorCount() {
        int count = 0;
        for (ShaderValidationDiagnostic diagnostic : diagnostics) {
            if (diagnostic != null && diagnostic.severity() == ShaderValidationSeverity.ERROR) {
                count++;
            }
        }
        return count;
    }

    /**
     * Returns the diagnostics.
     *
     * @return the diagnostics
     */
    public ShaderValidationDiagnostic[] diagnostics() {
        return diagnostics.clone();
    }

    /**
     * Runs the throw if failed step.
     *
     * @param label the debug label
     */
    public void throwIfFailed(String label) {
        if (successStatus()) {
            return;
        }
        StringBuilder builder = new StringBuilder();
        builder.append("Shader validation failed");
        if (label != null && label.length() > 0) {
            builder.append(" for ").append(label);
        }
        builder.append(':');
        for (ShaderValidationDiagnostic diagnostic : diagnostics) {
            if (diagnostic != null && diagnostic.severity() == ShaderValidationSeverity.ERROR) {
                builder.append('\n').append(diagnostic.code()).append(": ").append(diagnostic.message());
            }
        }
        throw new FdxException(builder.toString());
    }
}
