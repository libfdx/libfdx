package io.github.libfdx.tools.shader;

import java.nio.charset.StandardCharsets;

/**
 * Represents a native Tint bridge result.
 *
 * @author xpenatan
 */
public final class FdxTintCompilerBridgeResult {
    private final int status;
    private final FdxTintCompilerOutput outputKind;
    private final byte[] output;
    private final String diagnostics;

    private FdxTintCompilerBridgeResult(int status, FdxTintCompilerOutput outputKind, byte[] output,
            String diagnostics) {
        this.status = status;
        this.outputKind = outputKind != null ? outputKind : FdxTintCompilerOutput.NONE;
        this.output = output != null ? output.clone() : new byte[0];
        this.diagnostics = diagnostics != null ? diagnostics : "";
    }

    /**
     * Creates a result.
     *
     * @param status the status
     * @param outputKind the output kind
     * @param output the output
     * @param diagnostics the diagnostics
     * @return a new result
     */
    public static FdxTintCompilerBridgeResult of(int status, FdxTintCompilerOutput outputKind, byte[] output,
            String diagnostics) {
        return new FdxTintCompilerBridgeResult(status, outputKind, output, diagnostics);
    }

    /**
     * Creates a text result.
     *
     * @param text the text
     * @return a new result
     */
    public static FdxTintCompilerBridgeResult text(String text) {
        return new FdxTintCompilerBridgeResult(0, FdxTintCompilerOutput.TEXT,
                text != null ? text.getBytes(StandardCharsets.UTF_8) : new byte[0], "");
    }

    /**
     * Creates a failed result.
     *
     * @param diagnostics the diagnostics
     * @return a new result
     */
    public static FdxTintCompilerBridgeResult failure(String diagnostics) {
        return new FdxTintCompilerBridgeResult(1, FdxTintCompilerOutput.NONE, new byte[0], diagnostics);
    }

    public int status() {
        return status;
    }

    public boolean success() {
        return status == 0;
    }

    public FdxTintCompilerOutput outputKind() {
        return outputKind;
    }

    public byte[] output() {
        return output.clone();
    }

    public String outputText() {
        return new String(output, StandardCharsets.UTF_8);
    }

    public String diagnostics() {
        return diagnostics;
    }
}
