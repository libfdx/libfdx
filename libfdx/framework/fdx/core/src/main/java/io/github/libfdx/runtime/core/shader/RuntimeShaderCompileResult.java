package io.github.libfdx.runtime.core.shader;

import java.nio.charset.StandardCharsets;

/**
 * Represents a runtime shader compiler result.
 *
 * @author xpenatan
 */
public final class RuntimeShaderCompileResult {
    private static final RuntimeShaderCompileDiagnostic[] EMPTY_DIAGNOSTICS =
            new RuntimeShaderCompileDiagnostic[0];

    private final boolean success;
    private final RuntimeShaderCompileOutputKind outputKind;
    private final byte[] output;
    private final RuntimeShaderCompileDiagnostic[] diagnostics;

    private RuntimeShaderCompileResult(boolean success, RuntimeShaderCompileOutputKind outputKind, byte[] output,
            RuntimeShaderCompileDiagnostic[] diagnostics) {
        this.success = success;
        this.outputKind = outputKind != null ? outputKind : RuntimeShaderCompileOutputKind.NONE;
        this.output = output != null ? output.clone() : new byte[0];
        this.diagnostics = diagnostics != null ? diagnostics.clone() : EMPTY_DIAGNOSTICS;
    }

    /**
     * Creates a text result.
     *
     * @param text the text
     * @return a new result
     */
    public static RuntimeShaderCompileResult text(String text) {
        return new RuntimeShaderCompileResult(true, RuntimeShaderCompileOutputKind.TEXT,
                text != null ? text.getBytes(StandardCharsets.UTF_8) : new byte[0], EMPTY_DIAGNOSTICS);
    }

    /**
     * Creates a SPIR-V result.
     *
     * @param output the output
     * @return a new result
     */
    public static RuntimeShaderCompileResult spirv(byte[] output) {
        return new RuntimeShaderCompileResult(true, RuntimeShaderCompileOutputKind.SPIRV, output,
                EMPTY_DIAGNOSTICS);
    }

    /**
     * Creates a failed result.
     *
     * @param diagnostics the diagnostics
     * @return a new result
     */
    public static RuntimeShaderCompileResult failure(RuntimeShaderCompileDiagnostic[] diagnostics) {
        return new RuntimeShaderCompileResult(false, RuntimeShaderCompileOutputKind.NONE, new byte[0], diagnostics);
    }

    /**
     * Returns whether compilation succeeded.
     *
     * @return true if succeeded; false otherwise
     */
    public boolean success() {
        return success;
    }

    /**
     * Returns the output kind.
     *
     * @return the output kind
     */
    public RuntimeShaderCompileOutputKind outputKind() {
        return outputKind;
    }

    /**
     * Returns the output.
     *
     * @return the output
     */
    public byte[] output() {
        return output.clone();
    }

    /**
     * Returns the output text.
     *
     * @return the output text
     */
    public String outputText() {
        return new String(output, StandardCharsets.UTF_8);
    }

    /**
     * Returns the diagnostics.
     *
     * @return the diagnostics
     */
    public RuntimeShaderCompileDiagnostic[] diagnostics() {
        return diagnostics.clone();
    }
}
