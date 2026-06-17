package io.github.libfdx.tools.shader;

import java.nio.charset.StandardCharsets;

/**
 * Represents a shader compiler result.
 *
 * @author xpenatan
 */
public final class FdxShaderCompilerResult {
    private static final FdxShaderCompilerDiagnostic[] EMPTY_DIAGNOSTICS = new FdxShaderCompilerDiagnostic[0];

    private final boolean success;
    private final FdxTintCompilerOutput outputKind;
    private final byte[] output;
    private final FdxShaderCompilerDiagnostic[] diagnostics;

    private FdxShaderCompilerResult(boolean success, FdxTintCompilerOutput outputKind, byte[] output,
            FdxShaderCompilerDiagnostic[] diagnostics) {
        this.success = success;
        this.outputKind = outputKind != null ? outputKind : FdxTintCompilerOutput.NONE;
        this.output = output != null ? output.clone() : new byte[0];
        this.diagnostics = diagnostics != null ? diagnostics.clone() : EMPTY_DIAGNOSTICS;
    }

    /**
     * Creates a successful text result.
     *
     * @param text the text
     * @return a result
     */
    public static FdxShaderCompilerResult text(String text) {
        return new FdxShaderCompilerResult(true, FdxTintCompilerOutput.TEXT,
                text != null ? text.getBytes(StandardCharsets.UTF_8) : new byte[0], EMPTY_DIAGNOSTICS);
    }

    /**
     * Creates a successful binary result.
     *
     * @param kind the kind
     * @param output the output
     * @return a result
     */
    public static FdxShaderCompilerResult binary(FdxTintCompilerOutput kind, byte[] output) {
        return new FdxShaderCompilerResult(true, kind, output, EMPTY_DIAGNOSTICS);
    }

    /**
     * Creates a failed result.
     *
     * @param diagnostics the diagnostics
     * @return a result
     */
    public static FdxShaderCompilerResult failure(FdxShaderCompilerDiagnostic[] diagnostics) {
        return new FdxShaderCompilerResult(false, FdxTintCompilerOutput.NONE, new byte[0], diagnostics);
    }

    /**
     * Returns whether compilation succeeded.
     *
     * @return true if compilation succeeded; false otherwise
     */
    public boolean success() {
        return success;
    }

    /**
     * Returns the output kind.
     *
     * @return the output kind
     */
    public FdxTintCompilerOutput outputKind() {
        return outputKind;
    }

    /**
     * Returns the output bytes.
     *
     * @return the output bytes
     */
    public byte[] output() {
        return output.clone();
    }

    /**
     * Returns the output as text.
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
    public FdxShaderCompilerDiagnostic[] diagnostics() {
        return diagnostics.clone();
    }
}
