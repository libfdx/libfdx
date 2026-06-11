package io.github.libfdx.graphics;

import io.github.libfdx.core.FdxException;

public final class ShaderValidationResult {
    private static final ShaderValidationDiagnostic[] EMPTY_DIAGNOSTICS = new ShaderValidationDiagnostic[0];
    private static final ShaderValidationResult SUCCESS = new ShaderValidationResult(EMPTY_DIAGNOSTICS);

    private final ShaderValidationDiagnostic[] diagnostics;

    private ShaderValidationResult(ShaderValidationDiagnostic[] diagnostics) {
        this.diagnostics = diagnostics != null ? diagnostics.clone() : EMPTY_DIAGNOSTICS;
    }

    public static ShaderValidationResult success() {
        return SUCCESS;
    }

    public static ShaderValidationResult of(ShaderValidationDiagnostic[] diagnostics) {
        if (diagnostics == null || diagnostics.length == 0) {
            return SUCCESS;
        }
        return new ShaderValidationResult(diagnostics);
    }

    public boolean successStatus() {
        return errorCount() == 0;
    }

    public boolean isSuccess() {
        return successStatus();
    }

    public int errorCount() {
        int count = 0;
        for (ShaderValidationDiagnostic diagnostic : diagnostics) {
            if (diagnostic != null && diagnostic.severity() == ShaderValidationSeverity.ERROR) {
                count++;
            }
        }
        return count;
    }

    public ShaderValidationDiagnostic[] diagnostics() {
        return diagnostics.clone();
    }

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
