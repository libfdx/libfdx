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
    private final RuntimeShaderReflection reflection;
    private final RuntimeShaderTargetInterface targetInterface;

    private RuntimeShaderCompileResult(boolean success, RuntimeShaderCompileOutputKind outputKind, byte[] output,
            RuntimeShaderCompileDiagnostic[] diagnostics, RuntimeShaderReflection reflection,
            RuntimeShaderTargetInterface targetInterface) {
        this.success = success;
        this.outputKind = outputKind != null ? outputKind : RuntimeShaderCompileOutputKind.NONE;
        this.output = output != null ? output.clone() : new byte[0];
        this.diagnostics = diagnostics != null ? diagnostics.clone() : EMPTY_DIAGNOSTICS;
        this.reflection = reflection;
        this.targetInterface = targetInterface;
    }

    /**
     * Creates a text result.
     *
     * @param text the text
     * @return a new result
     */
    public static RuntimeShaderCompileResult text(String text) {
        return text(text, null);
    }

    /**
     * Creates a text result with compiler reflection.
     *
     * @param text the text
     * @param reflection the compiler reflection
     * @return a new result
     */
    public static RuntimeShaderCompileResult text(String text, RuntimeShaderReflection reflection) {
        return text(text, reflection, null);
    }

    /**
     * Creates a text result with compiler reflection and translated target-interface metadata.
     *
     * @param text the text
     * @param reflection the compiler reflection
     * @param targetInterface the translated target interface
     * @return a new result
     */
    public static RuntimeShaderCompileResult text(String text, RuntimeShaderReflection reflection,
            RuntimeShaderTargetInterface targetInterface) {
        return new RuntimeShaderCompileResult(true, RuntimeShaderCompileOutputKind.TEXT,
                text != null ? text.getBytes(StandardCharsets.UTF_8) : new byte[0],
                EMPTY_DIAGNOSTICS, reflection, targetInterface);
    }

    /**
     * Creates a SPIR-V result.
     *
     * @param output the output
     * @return a new result
     */
    public static RuntimeShaderCompileResult spirv(byte[] output) {
        return spirv(output, null);
    }

    /**
     * Creates a SPIR-V result with compiler reflection.
     *
     * @param output the output
     * @param reflection the compiler reflection
     * @return a new result
     */
    public static RuntimeShaderCompileResult spirv(byte[] output, RuntimeShaderReflection reflection) {
        return spirv(output, reflection, null);
    }

    /**
     * Creates a SPIR-V result with compiler reflection and translated target-interface metadata.
     *
     * @param output the output
     * @param reflection the compiler reflection
     * @param targetInterface the translated target interface
     * @return a new result
     */
    public static RuntimeShaderCompileResult spirv(byte[] output, RuntimeShaderReflection reflection,
            RuntimeShaderTargetInterface targetInterface) {
        return new RuntimeShaderCompileResult(true, RuntimeShaderCompileOutputKind.SPIRV, output,
                EMPTY_DIAGNOSTICS, reflection, targetInterface);
    }

    /**
     * Creates a failed result.
     *
     * @param diagnostics the diagnostics
     * @return a new result
     */
    public static RuntimeShaderCompileResult failure(RuntimeShaderCompileDiagnostic[] diagnostics) {
        return new RuntimeShaderCompileResult(false, RuntimeShaderCompileOutputKind.NONE, new byte[0], diagnostics,
                null, null);
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

    /**
     * Returns whether this result contains compiler reflection.
     *
     * @return true when reflection is available
     */
    public boolean hasReflection() {
        return reflection != null;
    }

    /**
     * Returns compiler reflection when available.
     *
     * @return the reflection, or null when this result has no reflection
     */
    public RuntimeShaderReflection reflection() {
        return reflection;
    }

    /**
     * Returns whether this result contains translated target-interface metadata.
     *
     * @return true when target metadata is available
     */
    public boolean hasTargetInterface() {
        return targetInterface != null;
    }

    /**
     * Returns the translated target interface when available.
     *
     * @return the target interface, or null
     */
    public RuntimeShaderTargetInterface targetInterface() {
        return targetInterface;
    }
}
